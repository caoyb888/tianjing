#!/usr/bin/env python3
"""
Redis 路由缓存恢复脚本
用途：从 PostgreSQL 查询所有 ACTIVE 场景，将路由配置写回 Redis。
     Redis 重启后 volatile key 丢失会导致 route-dispatch-service 静默丢帧，
     本脚本由 start-backend.sh 在服务启动完毕后自动调用，防止此类故障复现。

等效于 scene-config-service 的 syncRedisCache() 行为，但不依赖 Java 服务已就绪。
"""

import json
import os
import sys

# ─── 连接参数（优先读环境变量，回退到本地开发默认值）──────────────────────────
PG_HOST     = os.environ.get("TIANJING_POSTGRES_HOST",          "localhost")
PG_PORT     = int(os.environ.get("TIANJING_POSTGRES_PORT",      "5432"))
PG_DBNAME   = os.environ.get("TIANJING_POSTGRES_PROD_DBNAME",   "tianjing_prod")
PG_USER     = os.environ.get("TIANJING_POSTGRES_PROD_USER",     "tianjing_prod_user")
PG_PASSWORD = os.environ.get("TIANJING_POSTGRES_PROD_PASSWORD", "prod_user_dev_2024")

REDIS_HOST     = os.environ.get("TIANJING_REDIS_HOST",     "localhost")
REDIS_PORT     = int(os.environ.get("TIANJING_REDIS_PORT", "6379"))
REDIS_PASSWORD = os.environ.get("TIANJING_REDIS_PASSWORD", "tianjing_dev_redis_2024")

REDIS_KEY_PREFIX = "tianjing:scene:active:"

# DB factory_code → SceneConfigDetail factory 枚举值（与 SceneConfigDetail.normalizeFactory 对齐）
FACTORY_MAP = {
    "PELLET":  "pellet",
    "SINTER":  "sintering",
    "STEEL":   "steel",
    "SECTION": "section",
    "STRIP":   "strip",
}

# DB category → SceneConfigDetail category 枚举值（与 SceneConfigDetail.normalizeCategory 对齐）
CATEGORY_MAP = {
    "QUALITY_INSPECT":   "quality",
    "EQUIPMENT_MONITOR": "equipment",
    "PROCESS_PARAM":     "process",
}


def parse_jsonb(value):
    """psycopg2 会将 JSONB 列直接返回为 dict/list；字符串类型则尝试解析。"""
    if value is None:
        return None
    if isinstance(value, (dict, list)):
        return value
    try:
        return json.loads(value)
    except Exception:
        return value


def main():
    try:
        import psycopg2
    except ImportError:
        print("[ERROR] 缺少依赖：psycopg2-binary，请确认 uv run --with psycopg2-binary 已生效", file=sys.stderr)
        sys.exit(1)

    try:
        import redis as redis_lib
    except ImportError:
        print("[ERROR] 缺少依赖：redis，请确认 uv run --with redis 已生效", file=sys.stderr)
        sys.exit(1)

    # ─── 查询 PostgreSQL ───────────────────────────────────────────────────────
    try:
        conn = psycopg2.connect(
            host=PG_HOST, port=PG_PORT, dbname=PG_DBNAME,
            user=PG_USER, password=PG_PASSWORD,
            connect_timeout=5,
        )
    except Exception as e:
        print(f"[ERROR] PostgreSQL 连接失败 ({PG_HOST}:{PG_PORT}/{PG_DBNAME}): {e}", file=sys.stderr)
        sys.exit(1)

    try:
        with conn.cursor() as cur:
            cur.execute("""
                SELECT scene_id, scene_name, factory_code, process_code,
                       category, priority, prod_model_id, frame_interval,
                       algo_config_json, alarm_config_json,
                       workflow_json, version, created_at, updated_at, created_by
                FROM scene_config
                WHERE status = 'ACTIVE'
                  AND (is_deleted IS NULL OR is_deleted = false)
                ORDER BY scene_id
            """)
            rows = cur.fetchall()
    except Exception as e:
        print(f"[ERROR] 查询 scene_config 失败: {e}", file=sys.stderr)
        conn.close()
        sys.exit(1)
    finally:
        conn.close()

    if not rows:
        print("未找到 ACTIVE 场景，跳过 Redis 写入")
        return

    # ─── 连接 Redis ───────────────────────────────────────────────────────────
    try:
        r = redis_lib.Redis(
            host=REDIS_HOST, port=REDIS_PORT,
            password=REDIS_PASSWORD,
            decode_responses=True,
            socket_connect_timeout=5,
        )
        r.ping()
    except Exception as e:
        print(f"[ERROR] Redis 连接失败 ({REDIS_HOST}:{REDIS_PORT}): {e}", file=sys.stderr)
        sys.exit(1)

    # ─── 写入路由缓存 ─────────────────────────────────────────────────────────
    count = 0
    for row in rows:
        (scene_id, scene_name, factory_code, process_code,
         category, priority, prod_model_id, frame_interval,
         algo_config_json, alarm_config_json,
         workflow_json, version, created_at, updated_at, created_by) = row

        algo_config = parse_jsonb(algo_config_json)
        plugin_id   = algo_config.get("plugin_id", "") if isinstance(algo_config, dict) else ""

        # 格式与 SceneConfigService.syncRedisCache() 严格对齐：
        # 序列化 SceneConfigDetail 字段 + 追加 "active": true 布尔字段
        # 注意：不写入 roi_config_json（避免 SceneRouteConfig.RoiConfig 解析异常导致帧 ROI 被覆盖为 null）
        route_json = {
            "sceneId":             scene_id,
            "name":                scene_name,
            "factory":             FACTORY_MAP.get(factory_code or "", (factory_code or "").lower()),
            "processCode":         process_code,
            "category":            CATEGORY_MAP.get(category or "", (category or "").lower()),
            "priority":            priority,
            "status":              "active",
            "boundDeviceCode":     None,   # @TableField(exist=false)，DB 无此列
            "activeModelVersionId": prod_model_id,
            "activePluginId":      None,   # @TableField(exist=false)，DB 无此列
            "frameInterval":       frame_interval,
            "alarmConfig":         parse_jsonb(alarm_config_json),
            "algorithmConfig":     algo_config,
            "workflowJson":        parse_jsonb(workflow_json),
            "version":             version,
            "createdAt":           created_at.isoformat() if created_at else None,
            "updatedAt":           updated_at.isoformat() if updated_at else None,
            "createdBy":           created_by,
            "active":              True,   # 路由服务通过此布尔字段判断是否转发帧
        }

        key = REDIS_KEY_PREFIX + scene_id
        r.set(key, json.dumps(route_json, default=str, ensure_ascii=False))

        print(f"  写入: {key}  plugin={plugin_id or 'N/A'}")
        count += 1

    print(f"共恢复 {count} 条活跃场景路由配置至 Redis")


if __name__ == "__main__":
    main()
