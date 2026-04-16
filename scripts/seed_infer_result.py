#!/usr/bin/env python3
# =============================================================
# TDengine infer_result 演示数据写入脚本
# 用途：本地开发环境重置/补充近7天推理演示数据
# 场景：7个生产场景，每天推理量有波动，今天只写到当前时刻
# 运行：no_proxy='*' python3 scripts/seed_infer_result.py
#       （需绕过系统代理直连 TDengine HTTP 端口 6041）
# =============================================================
import urllib.request
import json
import base64
from datetime import datetime, timezone, timedelta

TDENGINE_URL = "http://localhost:6041/rest/sql/tianjing_ts"
AUTH_HEADER = "Basic " + base64.b64encode(b"root:taosdata").decode()

SCENES = [
    # (子表名, scene_id, factory, plugin_id, model_ver, top_class, base_ms, 日均帧数基准)
    ("ir_scene_pellet_001",      "SCENE-PELLET-001",      "PELLET",  "HEAD-SIDEPLATE-V1",     "MV-SIDEPLATE-20260322", "侧板跑偏", 18.0, 420),
    ("ir_scene_sinter_001",      "SCENE-SINTER-001",      "SINTER",  "HEAD-SINTER-GRATE-V1",  "MV-WALLBAR-20260322",   "壁条脱落", 16.5, 380),
    ("ir_scene_sinter_002",      "SCENE-SINTER-002",      "SINTER",  "ATOM-DETECT-YOLO-V1",   "MV-WALLBAR-20260322",   "篦条断裂", 20.1, 350),
    ("ir_scene_steel_001",       "SCENE-STEEL-001",       "STEEL",   "HEAD-BILLET-CRACK-V1",  "MV-BILLET-20260320",    "铸坯裂纹", 22.3, 460),
    ("ir_scene_section_001",     "SCENE-SECTION-001",     "SECTION", "HEAD-STEEL-SURFACE-V1", "MV-SURFACE-20260323",   "钢材划痕", 19.5, 310),
    ("ir_scene_strip_001",       "SCENE-STRIP-001",       "STRIP",   "HEAD-STRIP-SHEAR-V1",   "MV-BILLET-20260320",    "飞剪异常", 14.2, 280),
    ("ir_scene_sinter_fire_001", "SCENE-SINTER-FIRE-001", "SINTER",  "HEAD-FIRE-WATCH-V1",    "MV-FIRE-20260322",      "看火正常", 17.8, 330),
]

# 每天每场景的波动系数（day_offset=6最早，0=今天）
# 让数据呈现自然起伏，不完全单调
DAY_SCALE = [0.72, 0.85, 0.91, 0.78, 1.05, 0.96, 1.00]  # index 0=最早, 6=今天

START_HOUR_UTC = 0   # UTC 00:00（北京时间 08:00，工作日开始）
END_HOUR_UTC   = 14  # UTC 14:00（北京时间 22:00，工作日结束，保证在 UTC 当天分区内）

def send_sql(sql):
    data = sql.encode("utf-8")
    req = urllib.request.Request(TDENGINE_URL, data=data,
                                 headers={"Authorization": AUTH_HEADER,
                                          "Content-Type": "text/plain"})
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            resp = json.loads(r.read())
            if resp.get("code") != 0:
                print(f"  ERROR: {resp}")
            return resp
    except Exception as e:
        print(f"  REQUEST FAILED: {e}")
        return None

now_utc     = datetime.now(timezone.utc)
today_utc   = now_utc.replace(hour=0, minute=0, second=0, microsecond=0)

# 先清空近7天旧演示数据（幂等）
cutoff_ms = int((today_utc - timedelta(days=6)).timestamp() * 1000)
print(f"清除 {today_utc - timedelta(days=6):%Y-%m-%d} 起的旧数据...")
for (subtable, *_rest) in SCENES:
    send_sql(f"DELETE FROM {subtable} WHERE ts >= {cutoff_ms}")

total = 0
for day_offset in range(6, -1, -1):   # 6天前 → 今天
    day_idx   = 6 - day_offset         # 0=最早 → 6=今天
    day_base  = today_utc - timedelta(days=day_offset)
    scale     = DAY_SCALE[day_idx]

    # 今天只写到当前时刻，历史天写满整天
    if day_offset == 0:
        day_end = min(now_utc, day_base + timedelta(hours=END_HOUR_UTC))
    else:
        day_end = day_base + timedelta(hours=END_HOUR_UTC)

    for scene_idx, (subtable, scene_id, factory, plugin_id, model_ver, top_class, base_ms, daily_base) in enumerate(SCENES):
        # 根据日均帧数基准和当天波动系数计算间隔（秒），保证不同场景、不同天帧数不同
        scene_scale  = 0.9 + (scene_idx * 0.03)          # 场景微差
        daily_count  = int(daily_base * scale * scene_scale)
        work_seconds = (END_HOUR_UTC - START_HOUR_UTC) * 3600
        interval_sec = max(30, work_seconds // daily_count)  # 最小30秒间隔

        values_parts = []
        seq = day_offset * 100000 + scene_idx * 10000
        t   = day_base + timedelta(hours=START_HOUR_UTC)

        while t < day_end:
            seq += 1
            ts_ms = int(t.timestamp() * 1000)

            # 推理延迟：base ± 25% 随机抖动
            jitter   = ((seq * 17 + day_offset * 7 + scene_idx * 3) % 20) * 0.025 - 0.25
            infer_ms = round(base_ms * (1 + jitter), 2)

            # 置信度
            conf = round(0.72 + ((seq * 13 + day_offset * 3) % 25) * 0.010, 4)
            conf = min(conf, 0.99)

            # 异常帧（约 7%）
            is_anomaly = 1 if (seq % 14 == 0) else 0

            frame_id  = f"f{seq:06d}"
            image_url = (f"minio://tianjing-frames-prod/{factory.lower()}/"
                         f"{scene_id}/{day_base:%Y-%m}/"
                         f"{ts_ms}_{frame_id}.jpg")

            values_parts.append(
                f"({ts_ms}, '{frame_id}', {conf}, {is_anomaly}, "
                f"{infer_ms}, '{top_class}', NULL, "
                f"'{image_url}', '{plugin_id}', '{model_ver}')"
            )
            t += timedelta(seconds=interval_sec)

        # 每 200 行一批发送
        for i in range(0, len(values_parts), 200):
            send_sql(f"INSERT INTO {subtable} VALUES " + " ".join(values_parts[i:i+200]))

        total += len(values_parts)
        print(f"  ✓ {subtable:35s} day-{day_offset} ({day_base:%m-%d}): {len(values_parts):4d} 行")

print(f"\n共插入 {total} 行推理数据")
