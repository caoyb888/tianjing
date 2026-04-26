-- V13: 注册 CLASSIFY-FLAME-V1 模型版本（FLAME-20260423014138）
-- 补充 V12 迁移遗漏：V12 只注册了插件，未在 model_version 表中登记模型文件
--
-- 模型信息：
--   训练作业 ID  : FLAME-20260423014138
--   MinIO 路径   : tianjing-models-staging/CLASSIFY-FLAME-V1/FLAME-20260423014138/best.onnx
--   格式         : ONNX（EfficientNet-B2，CPU 推理）
--   验证集精度   : macroF1=1.0, acc=1.0（1876帧三录像验证集，第3 epoch达最优）
--   推理耗时     : CPU ≈ 45ms
--   模型大小     : 29.4 MB
--   状态         : STAGING（已接入推理链路，待正式走审批流转为 PRODUCTION）

-- ─────────────────────────────────────────────────────────────────
-- 1. 插入 model_version 记录
-- ─────────────────────────────────────────────────────────────────
INSERT INTO model_version (
    version_id,
    plugin_id,
    mlflow_run_id,
    mlflow_model_uri,
    model_path,
    export_format,
    precision_score,
    recall_score,
    inference_ms_cpu,
    model_size_mb,
    status,
    sandbox_hours,
    submitted_by,
    train_job_id,
    is_deleted,
    created_at,
    updated_at,
    created_by,
    updated_by
) VALUES (
    'MV-FLAME-20260423014138',
    'CLASSIFY-FLAME-V1',
    NULL,
    NULL,
    'tianjing-models-staging/CLASSIFY-FLAME-V1/FLAME-20260423014138/best.onnx',
    'ONNX',
    1.0000,   -- precision（macroF1=1.0 验证集，三分类任务用 F1 替代 mAP）
    1.0000,   -- recall
    45.000,   -- inference_ms_cpu（EfficientNet-B2 CPU 实测 ~45ms）
    29.40,    -- model_size_mb
    'STAGING',
    0,
    'system_migration_v13',
    'FLAME-20260423014138',
    false,
    '2026-04-23 01:52:35+00',   -- 模型上传完成时间（来自训练日志）
    NOW(),
    'system_migration_v13',
    'system_migration_v13'
)
ON CONFLICT (version_id) DO NOTHING;

-- ─────────────────────────────────────────────────────────────────
-- 2. 将 algorithm_plugin.current_model_version_id 指向本版本
--    （之前为 NULL，现更新为已注册的版本 ID）
-- ─────────────────────────────────────────────────────────────────
UPDATE algorithm_plugin
SET current_model_version_id = 'MV-FLAME-20260423014138',
    updated_at               = NOW(),
    updated_by               = 'system_migration_v13'
WHERE plugin_id = 'CLASSIFY-FLAME-V1'
  AND (current_model_version_id IS NULL OR current_model_version_id = '');

-- ─────────────────────────────────────────────────────────────────
-- 3. 操作日志（sys_operation_log 分区表，按月路由）
-- ─────────────────────────────────────────────────────────────────
INSERT INTO sys_operation_log (log_id, operator, operation, target_type, target_id, remark, operated_at)
VALUES (
    'LOG-V13-MODEL-0001',
    'system_migration_v13',
    'MODEL_REGISTER',
    'MODEL_VERSION',
    'MV-FLAME-20260423014138',
    'V13 迁移：注册 CLASSIFY-FLAME-V1 模型版本 FLAME-20260423014138',
    NOW()
) ON CONFLICT DO NOTHING;
