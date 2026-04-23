-- V12: 注册 CLASSIFY-FLAME-V1 分类插件并绑定烧结看火场景
-- 方法三阶段三：将 EfficientNet-B2 三分类模型接入生产推理链路
--
-- 变更内容：
--   1. algorithm_plugin 表插入 CLASSIFY-FLAME-V1 记录
--   2. scene_config 将 SCENE-SINTER-FIRE-001 的 plugin_id 从 CLOUD-PROXY-V1 改为 CLASSIFY-FLAME-V1
--      同步更新 workflow_json 中推理节点的 params.plugin_id
--   3. audit_log 记录本次迁移
--
-- 回滚方案（人工执行，无自动回滚）：
--   UPDATE scene_config
--     SET algo_config_json = jsonb_set(algo_config_json, '{plugin_id}', '"CLOUD-PROXY-V1"'),
--         workflow_json = (
--             SELECT jsonb_build_object(
--                 'nodes', jsonb_agg(
--                     CASE WHEN node->>'type' = 'inference'
--                         THEN jsonb_set(node, '{params,plugin_id}', '"CLOUD-PROXY-V1"')
--                         ELSE node
--                     END
--                 ),
--                 'edges', workflow_json->'edges'
--             ) FROM jsonb_array_elements(workflow_json->'nodes') node
--         ),
--         updated_at = NOW(), updated_by = 'rollback_v12', version = version + 1
--   WHERE scene_id = 'SCENE-SINTER-FIRE-001';

-- ─────────────────────────────────────────────────────────────────
-- 1. 注册 CLASSIFY-FLAME-V1 算法插件
-- ─────────────────────────────────────────────────────────────────
INSERT INTO algorithm_plugin (
    plugin_id,
    plugin_name,
    plugin_type,
    is_atom,
    parent_plugin_id,
    version,
    backbone,
    metadata_json,
    ui_schema_json,
    infer_backend,
    status,
    current_model_version_id,
    description,
    business_dimension,
    created_by,
    updated_by
) VALUES (
    'CLASSIFY-FLAME-V1',
    '烧结看火火焰强度分类',
    'CLASSIFICATION',
    true,
    NULL,
    '1.0.0',
    'EfficientNet-B2',
    -- metadata_json：算法元信息（CLAUDE.md §6.3）
    '{
        "plugin_id": "CLASSIFY-FLAME-V1",
        "type": "classification",
        "backbone": "EfficientNet-B2",
        "supported_scenes": ["SCENE-SINTER-FIRE-001"],
        "hardware_requirements": {
            "min_gpu_vram_gb": 0,
            "supports_tensorrt": false,
            "supports_onnx": true
        },
        "accuracy_metrics": {
            "macro_f1": 1.0,
            "inference_ms_cpu": 45
        },
        "labels": ["正常", "过弱", "料面不均"],
        "model_source": "tianjing-models-staging/CLASSIFY-FLAME-V1/FLAME-20260423014138/best.onnx",
        "service_endpoint": "http://localhost:8104",
        "health_endpoint": "http://localhost:8104/actuator/health",
        "infer_endpoint": "http://localhost:8104/infer",
        "notes": "方法三阶段二训练产出，1876帧三录像验证集 macroF1=1.0，阶段三接入推理链路"
    }'::jsonb,
    -- ui_schema_json：前端低代码编排器配置项
    '{
        "type": "object",
        "title": "看火分类参数配置",
        "properties": {
            "conf_threshold": {
                "type": "number",
                "title": "置信度阈值",
                "description": "低于此阈值的分类结果将被过滤（0.0 ~ 1.0）",
                "minimum": 0.0,
                "maximum": 1.0,
                "default": 0.85,
                "ui:widget": "slider",
                "ui:step": 0.01
            }
        },
        "required": ["conf_threshold"]
    }'::jsonb,
    'ONNX_CPU',
    'ACTIVE',
    NULL,
    'EfficientNet-B2 三分类（正常/过弱/料面不均），方法三阶段二训练，接入烧结看火场景实时推理链路',
    'SINTERING',
    'system_migration_v12',
    'system_migration_v12'
)
ON CONFLICT (plugin_id) DO UPDATE SET
    plugin_name              = EXCLUDED.plugin_name,
    version                  = EXCLUDED.version,
    backbone                 = EXCLUDED.backbone,
    metadata_json            = EXCLUDED.metadata_json,
    ui_schema_json           = EXCLUDED.ui_schema_json,
    infer_backend            = EXCLUDED.infer_backend,
    status                   = EXCLUDED.status,
    description              = EXCLUDED.description,
    business_dimension       = EXCLUDED.business_dimension,
    updated_by               = EXCLUDED.updated_by,
    updated_at               = NOW();

-- ─────────────────────────────────────────────────────────────────
-- 2. 绑定 SCENE-SINTER-FIRE-001 → CLASSIFY-FLAME-V1
--    同步更新 algo_config_json 和 workflow_json 推理节点
-- ─────────────────────────────────────────────────────────────────
UPDATE scene_config
SET
    -- 更新算法配置：plugin_id 改为 CLASSIFY-FLAME-V1，去掉 iou_threshold（分类任务不需要）
    algo_config_json = '{
        "plugin_id": "CLASSIFY-FLAME-V1",
        "conf_threshold": 0.85,
        "extra": {"backend_type": "onnx_cpu", "roi_enabled": true}
    }'::jsonb,
    -- 更新工作流推理节点的 plugin_id
    workflow_json = (
        SELECT jsonb_build_object(
            'nodes', jsonb_agg(
                CASE WHEN node->>'type' = 'inference'
                    THEN jsonb_set(
                             jsonb_set(node, '{params,plugin_id}', '"CLASSIFY-FLAME-V1"'),
                             '{label}', '"火焰强度识别"'
                         )
                    ELSE node
                END
                ORDER BY (node->>'id')
            ),
            'edges', workflow_json->'edges'
        )
        FROM jsonb_array_elements(workflow_json->'nodes') node
    ),
    updated_at  = NOW(),
    updated_by  = 'system_migration_v12',
    version     = version + 1
WHERE scene_id = 'SCENE-SINTER-FIRE-001'
  AND is_deleted = false;

-- ─────────────────────────────────────────────────────────────────
-- 3. 审计日志
-- ─────────────────────────────────────────────────────────────────
INSERT INTO audit_log (log_id, action_type, resource_type, resource_id, operator, trace_id, action_time)
VALUES (
    'LOG-V12-0001',
    'PLUGIN_REGISTER',
    'ALGORITHM_PLUGIN',
    'CLASSIFY-FLAME-V1',
    'system_migration_v12',
    'trace-v12-plugin',
    NOW()
),
(
    'LOG-V12-0002',
    'SCENE_UPDATE',
    'SCENE_CONFIG',
    'SCENE-SINTER-FIRE-001',
    'system_migration_v12',
    'trace-v12-scene',
    NOW()
)
ON CONFLICT (log_id) DO NOTHING;
