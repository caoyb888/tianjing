-- ============================================================
-- GPU 推理服务算法插件注册脚本
-- Sprint: GPU-04（V3.0 阶段二）
-- Plugin ID: LOCAL-GPU-YOLO-V1
--
-- 注册 V100 本地 GPU 推理服务为标准算法插件，
-- 用于 Sprint 3 烧结看火场景 GPU 推理端到端验证。
--
-- 执行方式：
--   sg docker -c "docker exec -i tianjing-postgresql \
--     psql -U tianjing_prod_user -d tianjing_prod" < scripts/seed_gpu_plugin.sql
-- ============================================================

-- 插件注册（idempotent：冲突时更新）
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
    'LOCAL-GPU-YOLO-V1',
    'V100 本地 GPU 目标检测引擎',
    'DETECTION',
    true,
    NULL,
    '1.0.0',
    'YOLOv8n',
    -- metadata_json：算法元信息（对应 CLAUDE.md §6.3 plugin.yaml）
    '{
        "plugin_id": "LOCAL-GPU-YOLO-V1",
        "type": "detection",
        "backbone": "YOLOv8n",
        "supported_scenes": ["SCENE-SINTER-*", "SCENE-STEEL-*", "SCENE-STRIP-*", "SCENE-PELLET-*"],
        "hardware_requirements": {
            "min_gpu_vram_gb": 4,
            "supports_tensorrt": true,
            "supports_onnx": true,
            "gpu_device": "Tesla V100-SXM2-32GB"
        },
        "accuracy_metrics": {
            "map50": null,
            "map50_95": null,
            "inference_ms_gpu_p95": null
        },
        "service_endpoint": "http://localhost:8102",
        "health_endpoint": "http://localhost:8102/actuator/health",
        "infer_endpoint": "http://localhost:8102/infer",
        "notes": "基础预训练 YOLOv8n 权重，Sprint 4 专用模型微调后更新 accuracy_metrics"
    }'::jsonb,
    -- ui_schema_json：前端低代码编排器可见的配置项（GPU-04 验收标准）
    '{
        "type": "object",
        "title": "GPU 推理参数配置",
        "properties": {
            "conf_threshold": {
                "type": "number",
                "title": "置信度阈值",
                "description": "低于此阈值的检测结果将被过滤（0.0 ~ 1.0）",
                "minimum": 0.0,
                "maximum": 1.0,
                "default": 0.25,
                "ui:widget": "slider",
                "ui:step": 0.01
            },
            "iou_threshold": {
                "type": "number",
                "title": "IoU 阈值（NMS）",
                "description": "非极大值抑制的 IoU 阈值（0.0 ~ 1.0）",
                "minimum": 0.0,
                "maximum": 1.0,
                "default": 0.45,
                "ui:widget": "slider",
                "ui:step": 0.01
            },
            "backend": {
                "type": "string",
                "title": "推理后端",
                "description": "onnx：ONNX Runtime GPU；tensorrt：TensorRT FP16（更快）",
                "enum": ["onnx", "tensorrt"],
                "default": "onnx",
                "ui:widget": "select"
            }
        },
        "required": ["conf_threshold", "iou_threshold"]
    }'::jsonb,
    'LOCAL_GPU',
    'ACTIVE',
    NULL,
    'V100 本地 GPU 推理引擎，支持 ONNX Runtime GPU / TensorRT FP16 双后端切换，服务端口 8102，P95 ≤20ms（TensorRT）',
    'SINTERING',
    'system',
    'system'
)
ON CONFLICT (plugin_id) DO UPDATE SET
    plugin_name              = EXCLUDED.plugin_name,
    version                  = EXCLUDED.version,
    metadata_json            = EXCLUDED.metadata_json,
    ui_schema_json           = EXCLUDED.ui_schema_json,
    infer_backend            = EXCLUDED.infer_backend,
    status                   = EXCLUDED.status,
    description              = EXCLUDED.description,
    business_dimension       = EXCLUDED.business_dimension,
    updated_by               = EXCLUDED.updated_by,
    updated_at               = NOW();

-- 验证插入结果
SELECT
    plugin_id,
    plugin_name,
    status,
    infer_backend,
    version,
    created_at
FROM algorithm_plugin
WHERE plugin_id = 'LOCAL-GPU-YOLO-V1';
