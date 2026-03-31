-- ============================================================
-- TDengine 时序库初始化脚本
-- Sprint：S0-02
-- 说明：创建超级表（STable）和烧结看火场景初始子表
--       执行方式：taos -h tdengine.middleware.svc.cluster.local < tdengine-init.sql
-- ============================================================

-- 创建数据库（生产库，保留 180 天）
CREATE DATABASE IF NOT EXISTS tianjing_ts
    KEEP 180
    DURATION 10
    BUFFER 16
    WAL_LEVEL 1
    VGROUPS 4
    CACHEMODEL 'last_value';

USE tianjing_ts;

-- ============================================================
-- 超级表：infer_result（推理结果高频时序，V2.0 已移除 bbox_json 大字段）
-- ============================================================
CREATE STABLE IF NOT EXISTS infer_result (
    ts              TIMESTAMP,
    frame_id        NCHAR(64),
    confidence      FLOAT,
    anomaly_count   INT,
    infer_ms        FLOAT,
    top_class_name  NCHAR(64),
    measurement_val FLOAT,
    image_url       NCHAR(512),
    plugin_id       NCHAR(64),
    model_version   NCHAR(32)
) TAGS (
    scene_id        NCHAR(64),
    factory_code    NCHAR(32),
    is_sandbox      BOOL
);

-- ============================================================
-- 超级表：camera_health_ts（摄像头健康时序，保留 365 天）
-- ============================================================
CREATE STABLE IF NOT EXISTS camera_health_ts (
    ts              TIMESTAMP,
    laplacian_var   FLOAT,
    avg_brightness  SMALLINT,
    optical_flow_x  FLOAT,
    optical_flow_y  FLOAT,
    health_score    SMALLINT,
    fault_type      NCHAR(16)
) TAGS (
    device_code     NCHAR(64),
    scene_id        NCHAR(64),
    factory_code    NCHAR(32)
);

-- ============================================================
-- 超级表：drift_metric_ts（漂移指标时序，永久保留）
-- ============================================================
CREATE DATABASE IF NOT EXISTS tianjing_drift
    KEEP 36500
    DURATION 365
    WAL_LEVEL 1;

USE tianjing_drift;

CREATE STABLE IF NOT EXISTS drift_metric_ts (
    ts              TIMESTAMP,
    precision_val   FLOAT,
    recall_val      FLOAT,
    f1_score        FLOAT,
    tp_count        INT,
    fp_count        INT,
    fn_count        INT,
    feedback_cov    FLOAT
) TAGS (
    scene_id        NCHAR(64),
    model_version   NCHAR(64)
);

-- ============================================================
-- 超级表：alarm_timeseries（告警时序索引，保留 365 天）
-- ============================================================
USE tianjing_ts;

CREATE STABLE IF NOT EXISTS alarm_timeseries (
    ts              TIMESTAMP,
    alarm_id        NCHAR(64),
    alarm_level     NCHAR(16),
    anomaly_type    NCHAR(64),
    confidence      FLOAT,
    is_resolved     BOOL
) TAGS (
    scene_id        NCHAR(64),
    factory_code    NCHAR(32),
    is_sandbox      BOOL
);

-- ============================================================
-- 初始化烧结看火场景子表（Sprint 3 验证场景预建）
-- ============================================================

-- 生产推理结果子表（TTL 180 天）
CREATE TABLE IF NOT EXISTS ir_scene_sinter_fire_001
    USING infer_result
    TAGS ('SCENE-SINTER-FIRE-001', 'SINTER', FALSE)
    TTL 15552000;

-- Sandbox 推理结果子表（TTL 30 天）
CREATE TABLE IF NOT EXISTS ir_scene_sinter_fire_001_sb
    USING infer_result
    TAGS ('SCENE-SINTER-FIRE-001', 'SINTER', TRUE)
    TTL 2592000;

-- 告警时序子表（生产）
CREATE TABLE IF NOT EXISTS alm_scene_sinter_fire_001
    USING alarm_timeseries
    TAGS ('SCENE-SINTER-FIRE-001', 'SINTER', FALSE)
    TTL 31536000;

-- 告警时序子表（Sandbox，仅用于对比分析）
CREATE TABLE IF NOT EXISTS alm_scene_sinter_fire_001_sb
    USING alarm_timeseries
    TAGS ('SCENE-SINTER-FIRE-001', 'SINTER', TRUE)
    TTL 2592000;
