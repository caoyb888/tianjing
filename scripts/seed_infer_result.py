#!/usr/bin/env python3
# =============================================================
# TDengine infer_result 演示数据写入脚本
# 用途：本地开发环境重置/补充近7天推理演示数据
# 场景：7个生产场景，每场景每天96条（10分钟间隔，06:00-22:00 UTC）
# 运行：no_proxy='*' python3 scripts/seed_infer_result.py
#       （需绕过系统代理直连 TDengine HTTP 端口 6041）
# =============================================================
import urllib.request
import urllib.error
import json
import math
from datetime import datetime, timezone, timedelta

TDENGINE_URL = "http://localhost:6041/rest/sql/tianjing_ts"
AUTH = ("root", "taosdata")
import base64
AUTH_HEADER = "Basic " + base64.b64encode(b"root:taosdata").decode()

SCENES = [
    ("ir_scene_pellet_001",   "SCENE-PELLET-001",    "PELLET",  "HEAD-SIDEPLATE-V1",     "MV-SIDEPLATE-20260322",  "侧板跑偏",   18.0),
    ("ir_scene_sinter_001",   "SCENE-SINTER-001",    "SINTER",  "HEAD-SINTER-GRATE-V1",  "MV-WALLBAR-20260322",    "壁条脱落",   16.5),
    ("ir_scene_sinter_002",   "SCENE-SINTER-002",    "SINTER",  "ATOM-DETECT-YOLO-V1",   "MV-WALLBAR-20260322",    "篦条断裂",   20.1),
    ("ir_scene_steel_001",    "SCENE-STEEL-001",     "STEEL",   "HEAD-BILLET-CRACK-V1",  "MV-BILLET-20260320",     "铸坯裂纹",   22.3),
    ("ir_scene_section_001",  "SCENE-SECTION-001",   "SECTION", "HEAD-STEEL-SURFACE-V1", "MV-SURFACE-20260323",    "钢材划痕",   19.5),
    ("ir_scene_strip_001",    "SCENE-STRIP-001",     "STRIP",   "HEAD-STRIP-SHEAR-V1",   "MV-BILLET-20260320",     "飞剪异常",   14.2),
    ("ir_scene_sinter_fire_001", "SCENE-SINTER-FIRE-001", "SINTER", "HEAD-FIRE-WATCH-V1", "MV-FIRE-20260322",     "看火正常",   17.8),
]

# 每天每场景生成多少行（间隔约 10 分钟, 06:00-22:00 UTC = 96 个点）
INTERVAL_MINUTES = 10
START_HOUR_UTC = 6
END_HOUR_UTC = 22

def send_sql(sql):
    data = sql.encode("utf-8")
    req = urllib.request.Request(TDENGINE_URL, data=data,
                                 headers={"Authorization": AUTH_HEADER,
                                          "Content-Type": "text/plain"})
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            resp = json.loads(r.read())
            if resp.get("code") != 0:
                print(f"  ERROR: {resp}")
            return resp
    except Exception as e:
        print(f"  REQUEST FAILED: {e}")
        return None

now_utc = datetime.now(timezone.utc)
today_utc = now_utc.replace(hour=0, minute=0, second=0, microsecond=0)

total = 0
for day_offset in range(6, -1, -1):   # 6天前 → 今天
    day_base = today_utc - timedelta(days=day_offset)

    for (subtable, scene_id, factory, plugin_id, model_ver, top_class, base_ms) in SCENES:
        values_parts = []
        seq = day_offset * 10000 + SCENES.index(
            (subtable, scene_id, factory, plugin_id, model_ver, top_class, base_ms)) * 1000

        t = day_base + timedelta(hours=START_HOUR_UTC)
        while t < day_base + timedelta(hours=END_HOUR_UTC):
            seq += 1
            ts_ms = int(t.timestamp() * 1000)

            # 推理延迟：base ± 20% 抖动
            jitter = ((seq * 17 + day_offset * 7) % 10) * 0.04 - 0.2
            infer_ms = round(base_ms * (1 + jitter), 2)

            # 置信度
            conf = round(0.75 + ((seq * 13 + day_offset * 3) % 20) * 0.010, 4)
            conf = min(conf, 0.99)

            # 异常帧（约 8%）
            is_anomaly = 1 if (seq % 13 == 0) else 0

            # frame_id
            frame_id = f"f{seq:05d}"

            image_url = (f"minio://tianjing-frames-prod/{factory.lower()}/"
                         f"{scene_id}/{day_base.strftime('%Y-%m')}/"
                         f"{ts_ms}_{frame_id}.jpg")

            values_parts.append(
                f"({ts_ms}, '{frame_id}', {conf}, {is_anomaly}, "
                f"{infer_ms}, '{top_class}', NULL, "
                f"'{image_url}', '{plugin_id}', '{model_ver}')"
            )
            t += timedelta(minutes=INTERVAL_MINUTES)

        # 每 200 行一批发送
        batch_size = 200
        for i in range(0, len(values_parts), batch_size):
            batch = values_parts[i:i+batch_size]
            sql = f"INSERT INTO {subtable} VALUES " + " ".join(batch)
            send_sql(sql)

        total += len(values_parts)
        print(f"  ✓ {subtable} day-{day_offset}: {len(values_parts)} 行")

print(f"\n共插入 {total} 行推理数据")
