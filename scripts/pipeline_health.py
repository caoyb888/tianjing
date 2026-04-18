#!/usr/bin/env python3
"""
天柱·天镜 推理链路健康检查工具
用法:
  python3 scripts/pipeline_health.py           # 检查一次
  python3 scripts/pipeline_health.py --watch   # 每 30 秒持续监控
  python3 scripts/pipeline_health.py --quiet   # 只输出异常项

检查范围：
  1. Docker 容器健康状态 + TDengine FD 资源使用率
  2. Kafka 消费者积压 + Topic 活跃度（生产帧通量）
  3. Java 微服务 /actuator/health 健康
  4. Python 推理服务 /health 健康
  5. TDengine 近期写入速率（REST API）
  6. 日志关键词扫描（近 5 分钟）
"""

import subprocess
import json
import time
import sys
import os
import re
import urllib.request
import urllib.error
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

# ─── 颜色 ─────────────────────────────────────────────────────────────────────
_tty = sys.stdout.isatty()
def _c(code, text): return f"\033[{code}m{text}\033[0m" if _tty else text
def GREEN(t):  return _c("0;32", t)
def RED(t):    return _c("0;31", t)
def YELLOW(t): return _c("1;33", t)
def CYAN(t):   return _c("0;36", t)
def BOLD(t):   return _c("1", t)
def DIM(t):    return _c("2", t)

OK       = GREEN("✅ OK   ")
WARN     = YELLOW("⚠️  WARN ")
CRIT     = RED("❌ CRIT ")
SKIP     = DIM("⏭  SKIP ")

LOG_DIR = Path("/tmp/tianjing-logs")
TDENGINE_REST = "http://localhost:6041/rest/sql"
TDENGINE_USER = "root"
TDENGINE_PASS = "taosdata"

# ─── 消费者组清单（name → 关注的 topic 关键字）────────────────────────────────
KAFKA_CONSUMER_GROUPS = {
    "route-dispatch-service-prod-cg":   "tianjing.frame.production",
    "result-aggregate-infer-prod-cg":   "tianjing.infer.result.production",
}

# 积压告警阈值
KAFKA_LAG_WARN  = 50
KAFKA_LAG_CRIT  = 500

# TDengine FD 告警阈值（百分比）
TDENGINE_FD_WARN_PCT  = 70
TDENGINE_FD_CRIT_PCT  = 90

# Java 微服务 (name, port)
JAVA_SERVICES = [
    ("auth-service",              8081),
    ("scene-config-service",      8082),
    ("device-manage-service",     8083),
    ("calibration-service",       8084),
    ("alarm-judge-service",       8085),
    ("alarm-rule-service",        8086),
    ("compare-dashboard-service", 8087),
    ("drift-monitor-service",     8089),
    ("health-monitor-service",    8090),
    ("route-dispatch-service",    8093),
    ("lowcode-workflow-service",  8094),
    ("notification-service",      8095),
    ("traffic-mirror-service",    8096),
    ("stream-ingest-service",     8097),
    ("frame-extract-service",     8098),
    ("preprocess-service",        8099),
    ("result-aggregate-service",  8100),
    ("history-replay-service",    8101),
]

# Python 推理服务 (name, port, health_path)
PYTHON_SERVICES = [
    ("gpu-infer-service",      8102, "/health"),
    ("infer-dispatcher",       8103, "/actuator/health"),
    ("cloud-inference-proxy",  8092, "/health"),
    ("recording-replay",       8091, "/actuator/health"),
]

# 日志关键词（优先级 → 正则列表）
LOG_CRITICAL_PATTERNS = [
    r"Too many open files",
    r"Node -1 disconnected",
    r"OutOfMemoryError",
    r"java\.lang\.StackOverflow",
    r"Cannot allocate memory",
    r"FATAL",
]
LOG_WARN_PATTERNS = [
    r"Connection refused",
    r"connect timed? ?out",
    r"Broken pipe",
    r"No space left on device",
    r"ERROR",
]

# ─────────────────────────────────────────────────────────────────────────────
# 工具函数
# ─────────────────────────────────────────────────────────────────────────────

def _run(cmd: str, timeout: int = 8) -> tuple[int, str, str]:
    """运行 shell 命令，返回 (returncode, stdout, stderr)"""
    try:
        r = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
        return r.returncode, r.stdout.strip(), r.stderr.strip()
    except subprocess.TimeoutExpired:
        return -1, "", "timeout"
    except Exception as e:
        return -1, "", str(e)


def _http_get(url: str, timeout: int = 5,
              user: Optional[str] = None, password: Optional[str] = None,
              method: str = "GET", data: Optional[bytes] = None,
              content_type: Optional[str] = None) -> tuple[int, str]:
    """HTTP 请求，返回 (http_status_or_-1, body)"""
    try:
        req = urllib.request.Request(url, data=data, method=method)
        if user:
            import base64
            creds = base64.b64encode(f"{user}:{password}".encode()).decode()
            req.add_header("Authorization", f"Basic {creds}")
        if content_type:
            req.add_header("Content-Type", content_type)
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return resp.status, resp.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", errors="replace")
    except Exception as e:
        return -1, str(e)


def _section(title: str):
    width = 64
    print(f"\n{BOLD('─' * width)}")
    print(BOLD(f"  {title}"))
    print(BOLD('─' * width))


def _row(status_icon: str, name: str, detail: str = ""):
    detail_str = f"  {DIM(detail)}" if detail else ""
    print(f"  {status_icon} {name:<40}{detail_str}")


# ─────────────────────────────────────────────────────────────────────────────
# 检查 1：Docker 容器健康状态
# ─────────────────────────────────────────────────────────────────────────────

def check_docker_containers() -> list[str]:
    """返回 critical 问题列表"""
    _section("1 / 6  Docker 容器健康")
    issues = []

    # {{.Status}} 已包含健康信息，如 "Up 8 hours (healthy)"
    code, out, _ = _run(
        r"""docker ps --format '{{.Names}}|{{.Status}}' 2>/dev/null"""
    )
    if code != 0:
        _row(CRIT, "docker", "docker ps 失败，Docker 未运行？")
        return ["docker daemon 不可达"]

    containers = {}
    for line in out.splitlines():
        parts = line.split("|", 1)
        if len(parts) == 2:
            name = parts[0].strip()
            status = parts[1].strip()
            # 从 Status 字符串提取括号内健康状态，如 "(healthy)" "(unhealthy)"
            m = re.search(r'\((\w+)\)', status)
            health = m.group(1).lower() if m else ""
            containers[name] = (status, health)

    EXPECTED = [
        "tianjing-kafka", "tianjing-redis", "tianjing-postgresql",
        "tianjing-minio", "tianjing-nacos", "tianjing-tdengine",
    ]
    for name in EXPECTED:
        if name not in containers:
            _row(CRIT, name, "容器不存在 / 未启动")
            issues.append(f"{name} 未启动")
            continue

        status, health = containers[name]
        up = status.lower().startswith("up")
        healthy = health.lower() in ("healthy", "")  # 无健康检查时 health 为空串

        if not up:
            _row(CRIT, name, f"状态={status}")
            issues.append(f"{name} 容器未运行: {status}")
        elif health.lower() == "unhealthy":
            _row(CRIT, name, f"unhealthy  状态={status}")
            issues.append(f"{name} unhealthy")
        elif health.lower() == "starting":
            _row(WARN, name, "health check 启动中…")
        else:
            _row(OK, name, status)

    # ── TDengine FD 使用率（重点：上次 Too many open files 的根因）
    _check_tdengine_fd(issues)

    return issues


def _check_tdengine_fd(issues: list[str]):
    """检查 taosd 进程的 FD 使用量，超阈值告警"""
    _docker = "docker"

    # pidof 可能返回多个 PID（主进程 + 子线程映射），取最小的那个（主进程 PID 最小）
    # 用 tr+cut 避免 awk 大括号与 Python str.format 冲突
    _fd_cmd = (
        "{docker} exec tianjing-tdengine sh -c "
        "'pid=$(pidof taosd 2>/dev/null | tr \" \" \"\\n\" | sort -n | head -1); "
        "[ -n \"$pid\" ] && cat /proc/$pid/limits | grep \"Max open files\" || true'"
    )
    code, out, _ = _run(_fd_cmd.format(docker=_docker))
    if code != 0 or not out.strip():
        _row(SKIP, "TDengine FD limit", "无法读取（taosd 未运行或容器不存在）")
        return

    # 格式：Max open files  1048576  1048576  files
    parts = out.split()
    try:
        nums = [p for p in parts if p.isdigit()]
        soft_limit = int(nums[-2]) if len(nums) >= 2 else int(nums[-1])
        hard_limit = int(nums[-1])
    except (ValueError, IndexError):
        _row(WARN, "TDengine FD limit", f"解析失败: {out!r}")
        return

    # 获取已用 FD 数（同样取最小 PID）
    _fd_used_cmd = (
        "{docker} exec tianjing-tdengine sh -c "
        "'pid=$(pidof taosd 2>/dev/null | tr \" \" \"\\n\" | sort -n | head -1); "
        "[ -n \"$pid\" ] && ls /proc/$pid/fd 2>/dev/null | wc -l || echo 0'"
    )
    code2, out2, _ = _run(_fd_used_cmd.format(docker=_docker))
    if code2 != 0:
        _row(SKIP, "TDengine FD 使用", "无法读取 fd 目录")
        return

    try:
        used = int(out2.strip())
    except ValueError:
        _row(WARN, "TDengine FD 使用", f"解析失败: {out2!r}")
        return

    pct = used / hard_limit * 100 if hard_limit > 0 else 0
    detail = f"使用={used:,}  上限={hard_limit:,}  占用={pct:.1f}%"

    if pct >= TDENGINE_FD_CRIT_PCT:
        _row(CRIT, "TDengine FD 使用", detail + "  ← 接近耗尽！")
        issues.append(f"TDengine FD 使用率 {pct:.1f}% (>{TDENGINE_FD_CRIT_PCT}%)，风险高")
    elif pct >= TDENGINE_FD_WARN_PCT:
        _row(WARN, "TDengine FD 使用", detail)
    else:
        _row(OK, "TDengine FD 使用", detail)


# ─────────────────────────────────────────────────────────────────────────────
# 检查 2：Kafka 消费者积压 + Topic 活跃度
# ─────────────────────────────────────────────────────────────────────────────

def check_kafka() -> list[str]:
    _section("2 / 6  Kafka 消费者积压 & Topic 活跃度")
    issues = []

    # ── 2a. 消费者组积压
    for group, topic_hint in KAFKA_CONSUMER_GROUPS.items():
        code, out, err = _run(
            f"docker exec tianjing-kafka kafka-consumer-groups "
            f"--bootstrap-server localhost:9092 --describe --group {group} 2>/dev/null",
            timeout=12
        )
        if code != 0 or not out.strip():
            _row(WARN, f"消费组 {group}", "无法查询（Kafka 未就绪 / 组未活跃）")
            continue

        # 解析 LAG 列
        total_lag = 0
        disconnected = False
        for line in out.splitlines():
            if "CONSUMER-ID" in line or line.startswith("GROUP") or not line.strip():
                continue
            cols = line.split()
            # 格式：GROUP TOPIC PARTITION CURRENT-OFFSET LOG-END-OFFSET LAG CONSUMER-ID HOST
            if len(cols) >= 6:
                lag_val = cols[5]
                if lag_val == "-":
                    disconnected = True
                elif lag_val.isdigit():
                    total_lag += int(lag_val)

        if disconnected:
            _row(CRIT, f"消费组 {group}",
                 f"消费者已断开（LAG='-'）  topic={topic_hint}")
            issues.append(f"Kafka 消费组 {group} 消费者断开，帧无法处理")
        elif total_lag >= KAFKA_LAG_CRIT:
            _row(CRIT, f"消费组 {group}",
                 f"积压 lag={total_lag:,}  topic={topic_hint}")
            issues.append(f"Kafka 消费组 {group} 积压 {total_lag}")
        elif total_lag >= KAFKA_LAG_WARN:
            _row(WARN, f"消费组 {group}",
                 f"积压 lag={total_lag:,}  topic={topic_hint}")
        else:
            _row(OK, f"消费组 {group}",
                 f"lag={total_lag}  topic={topic_hint}")

    # ── 2b. 生产帧 Topic 活跃度（采样间隔 3s 对比 offset 是否增长）
    _check_topic_throughput(issues)

    # ── 2c. route-dispatch-service 日志中检测 Kafka producer 断开
    _check_kafka_producer_disconnect(issues)

    return issues


def _check_topic_throughput(issues: list[str]):
    """采样 tianjing.frame.production 的 offset，判断是否有帧在流入"""
    topic = "tianjing.frame.production"

    def _get_offsets() -> Optional[int]:
        code, out, _ = _run(
            f"docker exec tianjing-kafka kafka-run-class kafka.tools.GetOffsetShell "
            f"--broker-list localhost:9092 --topic {topic} --time -1 2>/dev/null",
            timeout=10
        )
        if code != 0 or not out:
            return None
        total = 0
        for line in out.splitlines():
            parts = line.split(":")
            if len(parts) == 3:
                try:
                    total += int(parts[2])
                except ValueError:
                    pass
        return total

    off1 = _get_offsets()
    if off1 is None:
        _row(SKIP, f"Topic 活跃度 {topic}", "无法读取 offset")
        return

    time.sleep(3)
    off2 = _get_offsets()
    if off2 is None:
        _row(SKIP, f"Topic 活跃度 {topic}", "第二次采样失败")
        return

    delta = off2 - off1
    fps_approx = delta / 3.0
    if delta > 0:
        _row(OK, f"Topic 活跃度 {topic}",
             f"3s 内新增 {delta} 条（≈{fps_approx:.1f} fps）  总 offset={off2:,}")
    else:
        _row(WARN, f"Topic 活跃度 {topic}",
             f"3s 内无新消息（total offset={off2:,}）— 视频源未推流？")


def _check_kafka_producer_disconnect(issues: list[str]):
    """扫描 route-dispatch-service 日志中近 10 分钟的 Kafka producer 断开记录"""
    log_file = LOG_DIR / "route-dispatch-service.log"
    if not log_file.exists():
        return

    now_ts = time.time()
    found_disconnect = []
    PATTERN = re.compile(r"Node -1 disconnected|producer.*disconnect|DISCONNECT", re.IGNORECASE)
    TIME_WINDOW = 600  # 10 分钟

    try:
        # 只读最后 2000 行（避免大文件）
        code, out, _ = _run(f"tail -2000 {log_file}")
        for line in (out or "").splitlines():
            # 解析日志时间戳（格式：2026-04-17 09:11:58）
            m = re.match(r"(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})", line)
            if m:
                try:
                    ts = datetime.strptime(m.group(1), "%Y-%m-%d %H:%M:%S").timestamp()
                    if now_ts - ts <= TIME_WINDOW and PATTERN.search(line):
                        found_disconnect.append(line[:120])
                except ValueError:
                    pass
    except Exception:
        return

    if found_disconnect:
        for line in found_disconnect[-3:]:  # 最多展示 3 条
            _row(CRIT, "route-dispatch Kafka 生产者", line)
        issues.append(
            f"route-dispatch-service Kafka producer 在近 10 分钟内有断连记录（{len(found_disconnect)} 次）"
        )
    else:
        _row(OK, "route-dispatch Kafka producer", "近 10 分钟无断连记录")


# ─────────────────────────────────────────────────────────────────────────────
# 检查 3：Java 微服务 Actuator 健康
# ─────────────────────────────────────────────────────────────────────────────

def check_java_services() -> list[str]:
    _section("3 / 6  Java 微服务 Actuator 健康")
    issues = []

    for name, port in JAVA_SERVICES:
        status, body = _http_get(f"http://localhost:{port}/actuator/health", timeout=4)
        if status == -1:
            _row(CRIT, f"{name}:{port}", f"无响应 ({body[:60]})")
            issues.append(f"{name} (:{port}) 无响应")
        elif status == 200:
            try:
                data = json.loads(body)
                health_status = data.get("status", "UNKNOWN")
                if health_status == "UP":
                    _row(OK, f"{name}:{port}", "status=UP")
                else:
                    _row(WARN, f"{name}:{port}", f"status={health_status}")
                    issues.append(f"{name} actuator status={health_status}")
            except json.JSONDecodeError:
                _row(WARN, f"{name}:{port}", f"响应非 JSON: {body[:40]!r}")
        else:
            _row(WARN, f"{name}:{port}", f"HTTP {status}")

    return issues


# ─────────────────────────────────────────────────────────────────────────────
# 检查 4：Python 推理服务健康
# ─────────────────────────────────────────────────────────────────────────────

def check_python_services() -> list[str]:
    _section("4 / 6  Python 推理服务健康")
    issues = []

    for name, port, path in PYTHON_SERVICES:
        status, body = _http_get(f"http://localhost:{port}{path}", timeout=4)
        if status == -1:
            _row(CRIT, f"{name}:{port}", f"无响应 ({body[:60]})")
            issues.append(f"{name} (:{port}) 无响应")
        elif status == 200:
            # 兼容 {"status":"ok"} 和 {"status":"healthy"} 等格式
            try:
                data = json.loads(body)
                svc_status = data.get("status", "?")
                detail = ""
                if "model" in data:
                    detail = f"model={data['model']}"
                elif "plugin_id" in data:
                    detail = f"plugin={data['plugin_id']}"
                _row(OK, f"{name}:{port}", f"status={svc_status}  {detail}")
            except json.JSONDecodeError:
                _row(OK, f"{name}:{port}", body[:60])
        else:
            _row(WARN, f"{name}:{port}", f"HTTP {status}")

    return issues


# ─────────────────────────────────────────────────────────────────────────────
# 检查 5：TDengine 近期写入速率
# ─────────────────────────────────────────────────────────────────────────────

def check_tdengine_write_rate() -> list[str]:
    _section("5 / 6  TDengine 推理结果写入速率")
    issues = []

    def _query(sql: str) -> Optional[dict]:
        try:
            data = sql.encode("utf-8")
            status, body = _http_get(
                TDENGINE_REST, method="POST", data=data,
                content_type="text/plain; charset=UTF-8",
                user=TDENGINE_USER, password=TDENGINE_PASS
            )
            if status == 200:
                return json.loads(body)
        except Exception:
            pass
        return None

    # 近 2 分钟写入行数
    result = _query("SELECT COUNT(*) FROM tianjing_ts.infer_result WHERE ts > NOW - 2m")
    if result is None:
        _row(CRIT, "TDengine REST API", "无响应 (localhost:6041)")
        issues.append("TDengine REST API 不可达")
        return issues

    if result.get("code") != 0:
        _row(WARN, "TDengine 写入查询", f"error={result.get('desc','?')}")
        return issues

    try:
        count_2m = result["data"][0][0]
        rate_per_min = count_2m / 2.0
        detail = f"近 2 分钟写入 {count_2m} 行（≈{rate_per_min:.0f} 行/分钟）"
        if count_2m == 0:
            _row(WARN, "TDengine 写入速率", detail + "  — 推理链路未输出？视频源未推流？")
        else:
            _row(OK, "TDengine 写入速率", detail)
    except (IndexError, TypeError, KeyError):
        _row(WARN, "TDengine 写入速率", f"结果解析失败: {result}")
        return issues

    # 按 scene_id 分组，显示活跃场景
    result2 = _query(
        "SELECT scene_id, COUNT(*) AS cnt FROM tianjing_ts.infer_result "
        "WHERE ts > NOW - 5m GROUP BY scene_id ORDER BY cnt DESC LIMIT 5"
    )
    if result2 and result2.get("code") == 0:
        rows = result2.get("data", [])
        if rows:
            scene_list = ", ".join(f"{r[0]}({r[1]})" for r in rows)
            _row(OK, "活跃场景（近 5 分钟）", scene_list)

    return issues


# ─────────────────────────────────────────────────────────────────────────────
# 检查 6：日志关键词扫描（近 5 分钟）
# ─────────────────────────────────────────────────────────────────────────────

def check_logs() -> list[str]:
    _section("6 / 6  日志关键词扫描（近 5 分钟）")
    issues = []

    if not LOG_DIR.exists():
        _row(SKIP, "日志目录", f"{LOG_DIR} 不存在")
        return issues

    now_ts = time.time()
    TIME_WINDOW = 300  # 5 分钟

    # 核心链路服务的日志文件
    WATCH_LOGS = [
        "route-dispatch-service.log",
        "result-aggregate-service.log",
        "infer-dispatcher.log",
        "gpu-infer-service.log",
        "alarm-judge-service.log",
    ]

    crit_re = re.compile("|".join(LOG_CRITICAL_PATTERNS), re.IGNORECASE)
    warn_re = re.compile("|".join(LOG_WARN_PATTERNS), re.IGNORECASE)
    ts_re   = re.compile(r"(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})")

    any_hit = False
    for fname in WATCH_LOGS:
        log_file = LOG_DIR / fname
        if not log_file.exists():
            continue

        code, out, _ = _run(f"tail -1000 {log_file}")
        if code != 0 or not out:
            continue

        crit_hits = []
        warn_hits = []

        for line in out.splitlines():
            m = ts_re.match(line)
            if m:
                try:
                    ts = datetime.strptime(m.group(1), "%Y-%m-%d %H:%M:%S").timestamp()
                except ValueError:
                    continue
                if now_ts - ts > TIME_WINDOW:
                    continue
            else:
                continue  # 跳过无时间戳的行

            if crit_re.search(line):
                crit_hits.append(line[:120])
            elif warn_re.search(line):
                warn_hits.append(line[:120])

        service = fname.replace(".log", "")
        if crit_hits:
            any_hit = True
            _row(CRIT, service, f"{len(crit_hits)} 条严重错误（近 5 分钟）")
            for h in crit_hits[-2:]:
                print(f"      {DIM(h)}")
            issues.append(f"{service} 日志有 {len(crit_hits)} 条严重错误")
        elif warn_hits:
            any_hit = True
            _row(WARN, service, f"{len(warn_hits)} 条警告（近 5 分钟）")
            for h in warn_hits[-2:]:
                print(f"      {DIM(h)}")
        else:
            _row(OK, service, "近 5 分钟无告警")

    if not any_hit:
        pass  # 已逐条打印 OK

    return issues


# ─────────────────────────────────────────────────────────────────────────────
# 汇总输出
# ─────────────────────────────────────────────────────────────────────────────

def run_all_checks(quiet: bool = False) -> int:
    """执行全部检查。返回退出码：0=全部 OK，1=有 WARN，2=有 CRIT"""
    ts = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    print(f"\n{BOLD('=' * 64)}")
    print(f"{BOLD('  天柱·天镜 推理链路健康检查')}   {DIM(ts)}")
    print(BOLD('=' * 64))

    all_issues: list[str] = []

    all_issues += check_docker_containers()
    all_issues += check_kafka()
    all_issues += check_java_services()
    all_issues += check_python_services()
    all_issues += check_tdengine_write_rate()
    all_issues += check_logs()

    # ── 汇总
    print(f"\n{BOLD('─' * 64)}")
    if not all_issues:
        print(f"  {GREEN('✅  全部检查通过，链路健康')}")
        print(BOLD('─' * 64))
        return 0
    else:
        print(f"  {RED(f'发现 {len(all_issues)} 个问题：')}")
        for i, issue in enumerate(all_issues, 1):
            print(f"  {i:2d}. {issue}")
        print(BOLD('─' * 64))
        # 判断是否有 CRIT 级别
        has_crit = any(
            kw in issue for issue in all_issues
            for kw in ["不可达", "断开", "断连", "耗尽", "CRIT", "未运行", "unhealthy", "严重"]
        )
        return 2 if has_crit else 1


# ─────────────────────────────────────────────────────────────────────────────
# 入口
# ─────────────────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    watch  = "--watch"  in sys.argv
    quiet  = "--quiet"  in sys.argv
    interval = 30  # --watch 间隔秒数

    if watch:
        print(f"{CYAN(f'监控模式：每 {interval} 秒刷新一次，Ctrl+C 退出')}")
        exit_code = 0
        while True:
            try:
                exit_code = run_all_checks(quiet)
                print(f"\n{DIM(f'下次检查在 {interval} 秒后…（Ctrl+C 退出）')}\n")
                time.sleep(interval)
            except KeyboardInterrupt:
                print(f"\n{DIM('已退出监控模式')}")
                break
        sys.exit(exit_code)
    else:
        sys.exit(run_all_checks(quiet))
