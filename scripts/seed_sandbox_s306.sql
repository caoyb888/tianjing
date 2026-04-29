-- ============================================================
-- S3-06 Sandbox 会话候选模型注册脚本
-- Sprint 3 Story S3-06：GPU 生产模型 vs 候选模型双路推理
--
-- 注册一个 SANDBOX_VALIDATING 状态的候选模型版本，
-- 与生产版本（MV-A48890DF）使用相同插件（LOCAL-GPU-YOLO-V1），
-- 但 conf_threshold 配置不同（候选：0.5，生产：0.25）。
--
-- 执行方式：
--   sg docker -c "docker exec -i tianjing-postgresql \
--     psql -U tianjing_prod_user -d tianjing_prod" < scripts/seed_sandbox_s306.sql
-- ============================================================

INSERT INTO model_version (
    version_id,
    plugin_id,
    model_path,
    export_format,
    inference_ms_gpu,
    model_size_mb,
    status,
    sandbox_hours,
    is_deleted,
    created_by,
    updated_by,
    version
) VALUES (
    'MV-GPU-CAND-S306',
    'LOCAL-GPU-YOLO-V1',
    'tianjing-models-staging/LOCAL-GPU-YOLO-V1/yolov8n_fp16.trt',
    'TENSORRT',
    5.56,
    8.2,
    'SANDBOX_VALIDATING',
    0,
    false,
    'system_s306',
    'system_s306',
    1
)
ON CONFLICT (version_id) DO NOTHING;

-- 验证结果
SELECT version_id, plugin_id, status, inference_ms_gpu, model_path, created_at
FROM model_version
WHERE version_id IN ('MV-A48890DF', 'MV-GPU-CAND-S306')
ORDER BY created_at;
