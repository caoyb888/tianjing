"""
TDengine Schema 初始化脚本（REST API 方式）
通过 HTTP /rest/sql 逐条执行 tdengine-init.sql，避免 taosd native TCP 连接问题
"""
import os
import sys
import re
import time
import urllib.request
import urllib.error
import base64
import json

HOST     = os.environ.get("TDENGINE_HOST", "tdengine")
PORT     = os.environ.get("TDENGINE_PORT", "6041")
USER     = os.environ.get("TDENGINE_USER", "root")
PASSWORD = os.environ.get("TDENGINE_PASSWORD", "taosdata")
SQL_FILE = os.environ.get("SQL_FILE", "/scripts/tdengine-init.sql")

BASE_URL = f"http://{HOST}:{PORT}/rest/sql"
credentials = base64.b64encode(f"{USER}:{PASSWORD}".encode()).decode()
HEADERS = {"Authorization": f"Basic {credentials}", "Content-Type": "text/plain"}

_current_db = ""   # 跟踪当前 USE database 上下文


def exec_sql(sql: str) -> dict:
    global _current_db
    sql = sql.strip()
    if not sql:
        return {"code": 0}

    # 拦截 USE db 语句：不发送到服务器，仅更新本地上下文
    use_match = re.match(r'^USE\s+(\w+)\s*$', sql, re.IGNORECASE)
    if use_match:
        _current_db = use_match.group(1)
        return {"code": 0, "_intercepted": True}

    # 有上下文时把库名拼入 URL（REST API 无状态，需通过 URL 指定库）
    url = f"{BASE_URL}/{_current_db}" if _current_db else BASE_URL
    req = urllib.request.Request(url, data=sql.encode(), headers=HEADERS, method="POST")
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read())


def wait_ready(max_wait=120):
    print("等待 TDengine REST API 就绪...", flush=True)
    for i in range(max_wait // 3):
        try:
            result = exec_sql("show databases;")
            if result.get("code") == 0:
                print(f"  就绪（{(i+1)*3}s）", flush=True)
                return True
        except Exception:
            pass
        print(f"  未就绪，3s 后重试... ({(i+1)*3}s)", flush=True)
        time.sleep(3)
    return False


def parse_statements(sql_text: str) -> list[str]:
    """去除注释，按分号拆分 SQL 语句"""
    # 删除单行注释
    sql_text = re.sub(r'--[^\n]*', '', sql_text)
    # 删除多行注释
    sql_text = re.sub(r'/\*.*?\*/', '', sql_text, flags=re.DOTALL)
    stmts = [s.strip() for s in sql_text.split(';')]
    return [s for s in stmts if s and not s.isspace()]


def main():
    if not wait_ready():
        print("ERROR: TDengine 未在规定时间内就绪", file=sys.stderr)
        sys.exit(1)

    with open(SQL_FILE, encoding="utf-8") as f:
        sql_text = f.read()

    statements = parse_statements(sql_text)
    print(f"共解析到 {len(statements)} 条 SQL 语句，开始执行...", flush=True)

    errors = 0
    for idx, stmt in enumerate(statements, 1):
        first_line = stmt.split('\n')[0][:80]
        try:
            result = exec_sql(stmt)
            code = result.get("code", -1)
            if code == 0:
                print(f"  [{idx:3d}] ✓  {first_line}", flush=True)
            else:
                desc = result.get("desc", "unknown error")
                # code=0x2603(9731): table/db already exists — 忽略
                if code in (9731, 0x2603):
                    print(f"  [{idx:3d}] ~  已存在，跳过: {first_line}", flush=True)
                else:
                    print(f"  [{idx:3d}] ✗  {desc} | {first_line}", file=sys.stderr, flush=True)
                    errors += 1
        except Exception as e:
            print(f"  [{idx:3d}] ✗  异常: {e} | {first_line}", file=sys.stderr, flush=True)
            errors += 1

    if errors:
        print(f"\n⚠ 完成，{errors} 条语句失败", flush=True)
        sys.exit(1)
    else:
        print("\n>>> TDengine Schema 初始化完成 ✓", flush=True)


if __name__ == "__main__":
    main()
