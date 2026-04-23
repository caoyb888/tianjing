-- ============================================================
-- 烧结看火分类插件注册脚本（本地开发种子数据）
-- Plugin ID: CLASSIFY-FLAME-V1
-- 方法三阶段三：接入推理链路
--
-- 执行方式：
--   sg docker -c "docker exec -i tianjing-postgresql \
--     psql -U tianjing_prod_user -d tianjing_prod" < scripts/seed_classify_flame_plugin.sql
-- ============================================================

-- 与 V12__classify_flame_plugin.sql 内容一致，供本地手动执行（Flyway 不通时使用）

INSERT INTO algorithm_plugin (
    plugin_id, plugin_name, plugin_type, is_atom, parent_plugin_id,
    version, backbone, metadata_json, ui_schema_json, infer_backend,
    status, description, business_dimension, created_by, updated_by
) VALUES (
    'CLASSIFY-FLAME-V1',
    '烧结看火火焰强度分类',
    'CLASSIFICATION',
    true,
    NULL,
    '1.0.0',
    'EfficientNet-B2',
    '{
        "plugin_id": "CLASSIFY-FLAME-V1",
        "type": "classification",
        "backbone": "EfficientNet-B2",
        "supported_scenes": ["SCENE-SINTER-FIRE-001"],
        "hardware_requirements": {"min_gpu_vram_gb": 0, "supports_tensorrt": false, "supports_onnx": true},
        "accuracy_metrics": {"macro_f1": 1.0, "inference_ms_cpu": 45},
        "labels": ["正常", "过弱", "料面不均"],
        "model_source": "tianjing-models-staging/CLASSIFY-FLAME-V1/FLAME-20260423014138/best.onnx",
        "service_endpoint": "http://localhost:8104",
        "health_endpoint": "http://localhost:8104/actuator/health",
        "infer_endpoint": "http://localhost:8104/infer"
    }'::jsonb,
    '{
        "type": "object",
        "title": "看火分类参数配置",
        "properties": {
            "conf_threshold": {
                "type": "number", "title": "置信度阈值",
                "minimum": 0.0, "maximum": 1.0, "default": 0.85,
                "ui:widget": "slider", "ui:step": 0.01
            }
        },
        "required": ["conf_threshold"]
    }'::jsonb,
    'ONNX_CPU',
    'ACTIVE',
    'EfficientNet-B2 三分类（正常/过弱/料面不均），方法三阶段二训练，接入烧结看火场景实时推理链路',
    'SINTERING',
    'system',
    'system'
)
ON CONFLICT (plugin_id) DO UPDATE SET
    metadata_json  = EXCLUDED.metadata_json,
    status         = EXCLUDED.status,
    version        = EXCLUDED.version,
    updated_by     = EXCLUDED.updated_by,
    updated_at     = NOW();

-- 绑定场景
UPDATE scene_config
SET
    algo_config_json = '{"plugin_id":"CLASSIFY-FLAME-V1","conf_threshold":0.85,"extra":{"backend_type":"onnx_cpu","roi_enabled":true}}'::jsonb,
    workflow_json = (
        SELECT jsonb_build_object(
            'nodes', jsonb_agg(
                CASE WHEN node->>'type' = 'inference'
                    THEN jsonb_set(
                             jsonb_set(node, '{params,plugin_id}', '"CLASSIFY-FLAME-V1"'),
                             '{label}', '"火焰强度识别"'
                         )
                    ELSE node
                END ORDER BY (node->>'id')
            ),
            'edges', workflow_json->'edges'
        ) FROM jsonb_array_elements(workflow_json->'nodes') node
    ),
    updated_at = NOW(), updated_by = 'seed_classify_flame', version = version + 1
WHERE scene_id = 'SCENE-SINTER-FIRE-001' AND is_deleted = false;

SELECT plugin_id, plugin_name, status FROM algorithm_plugin WHERE plugin_id = 'CLASSIFY-FLAME-V1';
SELECT scene_id, algo_config_json->>'plugin_id' AS plugin FROM scene_config WHERE scene_id = 'SCENE-SINTER-FIRE-001';
