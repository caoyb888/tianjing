-- ============================================================
-- Flyway 迁移脚本 V4
-- 数据库：tianjing_prod（生产库）
-- 说明：永久测试数据集 — 系统管理、算法插件、场景配置、设备、告警规则
--       覆盖 16 个工业视觉检测场景、6 个测试账号、13 个算法插件
--       对应：测试计划 V1.0 § 3.3.2 测试账号规划 + 测试推进计划 V1.0
-- 编制：测试负责人 · 2026-04-02
-- ============================================================

-- ============================================================
-- PART 1：补充缺失工序（V2 未覆盖的 4 个工序）
-- ============================================================
INSERT INTO process (process_code, process_name, factory_code, sort_order, created_by, updated_by) VALUES
    ('SECTION_COLD_BED',        '冷床工序',         'SECTION', 3, 'SYSTEM', 'SYSTEM'),
    ('SECTION_HEATING_FURNACE', '加热炉工序',       'SECTION', 4, 'SYSTEM', 'SYSTEM'),
    ('STRIP_FLYING_SHEAR_AREA', '带钢飞剪工序',     'STRIP',   3, 'SYSTEM', 'SYSTEM'),
    ('STRIP_COILING',           '卷取工序',         'STRIP',   4, 'SYSTEM', 'SYSTEM');


-- ============================================================
-- PART 2：测试账号（6 个，对应测试计划 §3.3.2）
-- 默认密码：Test@Tianjing2026
-- 注意：password_hash 为占位符，部署前须用 BCrypt(cost=12) 替换
--       生成命令：htpasswd -bnBC 12 "" 'Test@Tianjing2026' | tr -d ':\n'
-- ============================================================
INSERT INTO sys_user (user_id, username, display_name, password_hash, dept_code, created_by, updated_by) VALUES
    ('USR-TEST-ADMIN',     'test_admin',      '测试管理员',     '{BCRYPT_TEST_ADMIN_PLACEHOLDER}',     'TEST', 'SYSTEM', 'SYSTEM'),
    ('USR-TEST-REV-A',     'test_reviewer_a', '测试审核员A',    '{BCRYPT_TEST_REVIEWER_A_PLACEHOLDER}', 'TEST', 'SYSTEM', 'SYSTEM'),
    ('USR-TEST-REV-B',     'test_reviewer_b', '测试审核员B',    '{BCRYPT_TEST_REVIEWER_B_PLACEHOLDER}', 'TEST', 'SYSTEM', 'SYSTEM'),
    ('USR-TEST-SANDBOX',   'test_sandbox_op', '测试实验室操作员', '{BCRYPT_TEST_SANDBOX_PLACEHOLDER}',  'TEST', 'SYSTEM', 'SYSTEM'),
    ('USR-TEST-EDITOR',    'test_scene_editor','测试场景编辑员', '{BCRYPT_TEST_EDITOR_PLACEHOLDER}',   'TEST', 'SYSTEM', 'SYSTEM'),
    ('USR-TEST-VIEWER',    'test_viewer',     '测试只读查看者',  '{BCRYPT_TEST_VIEWER_PLACEHOLDER}',   'TEST', 'SYSTEM', 'SYSTEM');

-- 角色绑定
INSERT INTO sys_user_role (user_id, role_code, granted_by) VALUES
    ('USR-TEST-ADMIN',   'ADMIN',            'SYSTEM'),
    ('USR-TEST-REV-A',   'MODEL_REVIEWER',   'SYSTEM'),
    ('USR-TEST-REV-B',   'MODEL_REVIEWER',   'SYSTEM'),
    ('USR-TEST-SANDBOX', 'SANDBOX_OPERATOR', 'SYSTEM'),
    ('USR-TEST-EDITOR',  'SCENE_EDITOR',     'SYSTEM'),
    ('USR-TEST-VIEWER',  'VIEWER',           'SYSTEM');


-- ============================================================
-- PART 3：算法插件
-- 3.1 原子算法（5 个，is_atom = TRUE）
-- ============================================================
INSERT INTO algorithm_plugin (
    plugin_id, plugin_name, plugin_type, is_atom, version,
    infer_backend, status, metadata_json, ui_schema_json,
    created_by, updated_by
) VALUES

-- 通用目标检测引擎（YOLOv9 骨干）
(
    'ATOM-DETECT-YOLO-V1', '通用目标检测引擎', 'DETECTION', TRUE, '1.2.0',
    'ONNX_CPU', 'ACTIVE',
    '{"backbone":"YOLOv9","supported_scenes":["链篦机侧板跑偏","烧结机壁条检测","铸坯表面缺陷","台车篦条缺损"],"hardware_requirements":{"min_gpu_vram_gb":4,"supports_tensorrt":true,"supports_onnx":true},"accuracy_metrics":{"map50":0.91,"map50_95":0.76,"inference_ms_gpu":18,"inference_ms_cpu":210}}',
    '{"properties":{"conf_threshold":{"type":"number","title":"置信度阈值","minimum":0.1,"maximum":1.0,"default":0.85,"ui:widget":"slider"},"iou_threshold":{"type":"number","title":"IoU阈值","minimum":0.1,"maximum":1.0,"default":0.45,"ui:widget":"slider"},"target_classes":{"type":"array","title":"检测目标类别","items":{"type":"string"},"ui:widget":"tag_select"},"roi_enabled":{"type":"boolean","title":"启用ROI区域","default":true,"ui:widget":"switch"}},"required":["conf_threshold"]}',
    'SYSTEM', 'SYSTEM'
),

-- 通用语义分割引擎（SAM 骨干）
(
    'ATOM-SEGMENT-SAM-V1', '通用语义分割引擎', 'SEGMENTATION', TRUE, '1.0.0',
    'ONNX_CPU', 'ACTIVE',
    '{"backbone":"SAM-ViT-B","supported_scenes":["铸坯裂纹","钢材表面缺陷","带钢端面质检"],"hardware_requirements":{"min_gpu_vram_gb":8,"supports_tensorrt":true,"supports_onnx":true},"accuracy_metrics":{"map50":0.88,"map50_95":0.71,"inference_ms_gpu":35,"inference_ms_cpu":480}}',
    '{"properties":{"conf_threshold":{"type":"number","title":"分割置信度","minimum":0.1,"maximum":1.0,"default":0.80,"ui:widget":"slider"},"mask_threshold":{"type":"number","title":"掩码二值化阈值","minimum":0.1,"maximum":0.9,"default":0.5,"ui:widget":"slider"},"roi_enabled":{"type":"boolean","title":"启用ROI区域","default":true,"ui:widget":"switch"}},"required":["conf_threshold"]}',
    'SYSTEM', 'SYSTEM'
),

-- 通用图像分类器（ResNet 骨干）
(
    'ATOM-CLASSIFY-RESNET-V1', '通用图像分类器', 'CLASSIFICATION', TRUE, '1.1.0',
    'ONNX_CPU', 'ACTIVE',
    '{"backbone":"ResNet50","supported_scenes":["分料器运行状态","吹氩站底吹"],"hardware_requirements":{"min_gpu_vram_gb":2,"supports_tensorrt":true,"supports_onnx":true},"accuracy_metrics":{"top1_acc":0.94,"inference_ms_gpu":8,"inference_ms_cpu":85}}',
    '{"properties":{"conf_threshold":{"type":"number","title":"分类置信度阈值","minimum":0.1,"maximum":1.0,"default":0.90,"ui:widget":"slider"},"top_k":{"type":"integer","title":"返回TopK类别数","minimum":1,"maximum":5,"default":1,"ui:widget":"number"}},"required":["conf_threshold"]}',
    'SYSTEM', 'SYSTEM'
),

-- 亚像素测量标定组件
(
    'ATOM-MEASURE-SUBPIXEL-V1', '亚像素测量标定组件', 'MEASUREMENT', TRUE, '1.0.0',
    'ONNX_CPU', 'ACTIVE',
    '{"backbone":"EdgeNet-SubPixel","supported_scenes":["型钢冷床尺寸测量","飞剪优化剪切","链篦机侧板偏移量测量"],"hardware_requirements":{"min_gpu_vram_gb":1,"supports_tensorrt":false,"supports_onnx":true},"accuracy_metrics":{"measurement_error_mm":0.08,"inference_ms_gpu":null,"inference_ms_cpu":45}}',
    '{"properties":{"scale_mm_per_px":{"type":"number","title":"比例尺（mm/px）","minimum":0.001,"default":0.5,"ui:widget":"number","ui:description":"由在线标定工具自动写入，勿手动修改"},"edge_threshold":{"type":"number","title":"边缘检测阈值","minimum":10,"maximum":200,"default":50,"ui:widget":"slider"},"measure_axis":{"type":"string","title":"测量轴向","enum":["HORIZONTAL","VERTICAL","BOTH"],"default":"HORIZONTAL","ui:widget":"select"}},"required":["scale_mm_per_px"]}',
    'SYSTEM', 'SYSTEM'
),

-- 去雾/增强预处理组件
(
    'ATOM-ENHANCE-DEHAZE-V1', '图像去雾增强预处理', 'ENHANCEMENT', TRUE, '1.0.0',
    'ONNX_CPU', 'ACTIVE',
    '{"backbone":"DehazingNet","supported_scenes":["烧结看火场景","转炉溅渣密度"],"hardware_requirements":{"min_gpu_vram_gb":1,"supports_tensorrt":true,"supports_onnx":true},"accuracy_metrics":{"psnr_improvement_db":3.5,"inference_ms_gpu":6,"inference_ms_cpu":40}}',
    '{"properties":{"enhance_level":{"type":"string","title":"增强强度","enum":["LOW","MEDIUM","HIGH"],"default":"MEDIUM","ui:widget":"select"},"dehaze_enabled":{"type":"boolean","title":"启用去雾","default":false,"ui:widget":"switch"},"brightness_normalize":{"type":"boolean","title":"亮度归一化","default":true,"ui:widget":"switch"}},"required":["enhance_level"]}',
    'SYSTEM', 'SYSTEM'
);


-- ============================================================
-- 3.2 场景任务头（8 个，is_atom = FALSE，挂载在原子算法下）
-- ============================================================
INSERT INTO algorithm_plugin (
    plugin_id, plugin_name, plugin_type, is_atom, parent_plugin_id, version,
    infer_backend, status, metadata_json, ui_schema_json,
    created_by, updated_by
) VALUES

-- 链篦机侧板偏移检测头
(
    'HEAD-SIDEPLATE-V1', '链篦机侧板偏移检测头', 'DETECTION', FALSE, 'ATOM-DETECT-YOLO-V1', '1.0.0',
    'ONNX_CPU', 'ACTIVE',
    '{"task":"sideplate_deviation","supported_scenes":["链篦机侧板跑偏检测"],"accuracy_metrics":{"map50":0.89,"inference_ms_gpu":20,"inference_ms_cpu":230},"anomaly_classes":["侧板左偏","侧板右偏","侧板脱轨"]}',
    '{"properties":{"conf_threshold":{"type":"number","title":"偏移检测置信度","minimum":0.5,"maximum":1.0,"default":0.85,"ui:widget":"slider"},"deviation_threshold_mm":{"type":"number","title":"偏移报警阈值(mm)","minimum":5,"maximum":50,"default":15,"ui:widget":"slider"},"measure_enabled":{"type":"boolean","title":"启用偏移量测量","default":true,"ui:widget":"switch"}},"required":["conf_threshold"]}',
    'SYSTEM', 'SYSTEM'
),

-- 篦条缺损检测头（烧结台车篦条 + 球团链篦机）
(
    'HEAD-GRATE-BAR-V1', '篦条缺损检测头', 'DETECTION', FALSE, 'ATOM-DETECT-YOLO-V1', '1.0.0',
    'ONNX_CPU', 'ACTIVE',
    '{"task":"grate_bar_defect","supported_scenes":["台车篦条缺损检测","链篦机篦板检测"],"accuracy_metrics":{"map50":0.87,"inference_ms_gpu":22,"inference_ms_cpu":240},"anomaly_classes":["篦条断裂","篦条缺失","篦条变形","篦条堵塞"]}',
    '{"properties":{"conf_threshold":{"type":"number","title":"缺损检测置信度","minimum":0.5,"maximum":1.0,"default":0.85,"ui:widget":"slider"},"min_defect_size_px":{"type":"integer","title":"最小缺陷像素尺寸","minimum":5,"maximum":50,"default":15,"ui:widget":"number"}},"required":["conf_threshold"]}',
    'SYSTEM', 'SYSTEM'
),

-- 钢材表面缺陷检测头（型钢冷床）
(
    'HEAD-STEEL-SURFACE-V1', '钢材表面缺陷检测头', 'DETECTION', FALSE, 'ATOM-DETECT-YOLO-V1', '1.0.0',
    'ONNX_CPU', 'ACTIVE',
    '{"task":"steel_surface_defect","supported_scenes":["冷床入口钢材质量","检查台架钢材质量","加热炉铸坯质量"],"accuracy_metrics":{"map50":0.90,"inference_ms_gpu":19,"inference_ms_cpu":220},"anomaly_classes":["划伤","折叠","结疤","裂纹","凹坑"]}',
    '{"properties":{"conf_threshold":{"type":"number","title":"缺陷置信度","minimum":0.5,"maximum":1.0,"default":0.85,"ui:widget":"slider"},"defect_types":{"type":"array","title":"检测缺陷类型","items":{"type":"string"},"default":["划伤","折叠","结疤"],"ui:widget":"tag_select"},"min_area_ratio":{"type":"number","title":"最小缺陷面积比","minimum":0.0001,"maximum":0.1,"default":0.001,"ui:widget":"slider"}},"required":["conf_threshold"]}',
    'SYSTEM', 'SYSTEM'
),

-- 铸坯裂纹检测头（炼钢连铸）
(
    'HEAD-BILLET-CRACK-V1', '铸坯裂纹检测头', 'SEGMENTATION', FALSE, 'ATOM-SEGMENT-SAM-V1', '1.0.0',
    'ONNX_CPU', 'ACTIVE',
    '{"task":"billet_crack","supported_scenes":["铸坯表面缺陷识别"],"accuracy_metrics":{"map50":0.86,"inference_ms_gpu":38,"inference_ms_cpu":510},"anomaly_classes":["纵裂纹","横裂纹","角裂纹","中间裂纹"]}',
    '{"properties":{"conf_threshold":{"type":"number","title":"裂纹置信度","minimum":0.5,"maximum":1.0,"default":0.82,"ui:widget":"slider"},"crack_width_threshold_mm":{"type":"number","title":"裂纹宽度报警阈值(mm)","minimum":0.1,"maximum":5.0,"default":0.5,"ui:widget":"slider"},"surface_coverage":{"type":"string","title":"检测面覆盖","enum":["TOP_ONLY","MULTI_FACE"],"default":"MULTI_FACE","ui:widget":"select"}},"required":["conf_threshold"]}',
    'SYSTEM', 'SYSTEM'
),

-- 料面高度估计头（烧结看火 + 皮带料面）
(
    'HEAD-MATERIAL-LEVEL-V1', '料面高度估计头', 'CLASSIFICATION', FALSE, 'ATOM-CLASSIFY-RESNET-V1', '1.0.0',
    'ONNX_CPU', 'ACTIVE',
    '{"task":"material_level","supported_scenes":["点火料面检测","成一皮带料面检测","烧结看火场景"],"accuracy_metrics":{"top1_acc":0.93,"inference_ms_gpu":9,"inference_ms_cpu":90},"level_classes":["料面过低","料面正常","料面过高","局部偏料"]}',
    '{"properties":{"conf_threshold":{"type":"number","title":"料面判断置信度","minimum":0.6,"maximum":1.0,"default":0.88,"ui:widget":"slider"},"alarm_on_low":{"type":"boolean","title":"料面过低时报警","default":true,"ui:widget":"switch"},"alarm_on_high":{"type":"boolean","title":"料面过高时报警","default":true,"ui:widget":"switch"}},"required":["conf_threshold"]}',
    'SYSTEM', 'SYSTEM'
),

-- 分料器状态分类头
(
    'HEAD-FEEDER-STATE-V1', '分料器运行状态分类头', 'CLASSIFICATION', FALSE, 'ATOM-CLASSIFY-RESNET-V1', '1.0.0',
    'ONNX_CPU', 'ACTIVE',
    '{"task":"feeder_state","supported_scenes":["分料器运行状态检测"],"accuracy_metrics":{"top1_acc":0.96,"inference_ms_gpu":7,"inference_ms_cpu":80},"state_classes":["正常运行","停机","偏转异常","卡料"]}',
    '{"properties":{"conf_threshold":{"type":"number","title":"状态分类置信度","minimum":0.7,"maximum":1.0,"default":0.90,"ui:widget":"slider"},"alert_on_stop":{"type":"boolean","title":"停机时立即报警","default":true,"ui:widget":"switch"}},"required":["conf_threshold"]}',
    'SYSTEM', 'SYSTEM'
),

-- 渣粒密度估计头（转炉溅渣）
(
    'HEAD-SLAG-DENSITY-V1', '渣粒密度估计头', 'CLASSIFICATION', FALSE, 'ATOM-CLASSIFY-RESNET-V1', '1.0.0',
    'ONNX_CPU', 'ACTIVE',
    '{"task":"slag_density","supported_scenes":["转炉溅渣渣粒密度"],"accuracy_metrics":{"top1_acc":0.89,"inference_ms_gpu":8,"inference_ms_cpu":88},"density_classes":["密度过低","密度适中","密度过高"]}',
    '{"properties":{"conf_threshold":{"type":"number","title":"密度判断置信度","minimum":0.6,"maximum":1.0,"default":0.85,"ui:widget":"slider"},"density_alarm_level":{"type":"string","title":"密度报警级别","enum":["LOW_ONLY","HIGH_ONLY","BOTH_EXTREMES"],"default":"BOTH_EXTREMES","ui:widget":"select"}},"required":["conf_threshold"]}',
    'SYSTEM', 'SYSTEM'
),

-- 尺寸测量回归头（型钢冷床尺寸 + 飞剪切头）
(
    'HEAD-DIMENSION-V1', '尺寸测量回归头', 'MEASUREMENT', FALSE, 'ATOM-MEASURE-SUBPIXEL-V1', '1.0.0',
    'ONNX_CPU', 'ACTIVE',
    '{"task":"dimension_measure","supported_scenes":["冷床入口钢材尺寸","飞剪优化剪切"],"accuracy_metrics":{"measurement_error_mm":0.06,"inference_ms_gpu":null,"inference_ms_cpu":50}}',
    '{"properties":{"scale_mm_per_px":{"type":"number","title":"比例尺（mm/px）","minimum":0.001,"default":0.5,"ui:widget":"number","ui:description":"由在线标定工具自动写入"},"width_nominal_mm":{"type":"number","title":"标称宽度(mm)","minimum":10,"maximum":1000,"default":200,"ui:widget":"number"},"tolerance_mm":{"type":"number","title":"公差范围(±mm)","minimum":0.5,"maximum":20,"default":3.0,"ui:widget":"slider"}},"required":["scale_mm_per_px","width_nominal_mm"]}',
    'SYSTEM', 'SYSTEM'
);


-- ============================================================
-- PART 4：模型版本（P1 场景 + Sprint 3 场景，共 6 个版本）
-- 状态说明：
--   P1 场景核心模型 → PRODUCTION（已上线）
--   Sprint 3 场景   → PRODUCTION（烧结看火专用）
--   候选模型        → SANDBOX_VALIDATING（用于四眼原则、转正流程测试）
-- ============================================================
INSERT INTO model_version (
    version_id, plugin_id, model_path, export_format,
    map50, map50_95, precision_score, recall_score,
    inference_ms_gpu, inference_ms_cpu, model_size_mb,
    status, sandbox_hours,
    submitted_by, created_by, updated_by
) VALUES

-- 铸坯裂纹检测：生产版本
('MV-BILLET-CRACK-20260320-001', 'HEAD-BILLET-CRACK-V1',
 'minio://tianjing-models-prod/steel/billet-crack/v1.0.0/model.onnx', 'ONNX',
 0.863, 0.718, 0.891, 0.842,
 38.0, 510.0, 85.6,
 'PRODUCTION', 72,
 'USR-TEST-REV-A', 'SYSTEM', 'SYSTEM'),

-- 链篦机侧板检测：生产版本
('MV-SIDEPLATE-20260322-001', 'HEAD-SIDEPLATE-V1',
 'minio://tianjing-models-prod/pellet/sideplate/v1.0.0/model.onnx', 'ONNX',
 0.887, 0.745, 0.902, 0.875,
 20.0, 230.0, 62.3,
 'PRODUCTION', 60,
 'USR-TEST-REV-A', 'SYSTEM', 'SYSTEM'),

-- 烧结机壁条检测：生产版本（YOLO 直接检测，不用任务头）
('MV-WALLBAR-20260322-001', 'ATOM-DETECT-YOLO-V1',
 'minio://tianjing-models-prod/sinter/wallbar/v1.0.0/model.onnx', 'ONNX',
 0.910, 0.762, 0.923, 0.888,
 18.0, 215.0, 58.0,
 'PRODUCTION', 55,
 'USR-TEST-REV-A', 'SYSTEM', 'SYSTEM'),

-- 钢材表面缺陷：生产版本
('MV-STEEL-SURFACE-20260323-001', 'HEAD-STEEL-SURFACE-V1',
 'minio://tianjing-models-prod/section/steel-surface/v1.0.0/model.onnx', 'ONNX',
 0.898, 0.756, 0.915, 0.881,
 19.0, 225.0, 65.2,
 'PRODUCTION', 50,
 'USR-TEST-REV-B', 'SYSTEM', 'SYSTEM'),

-- 烧结看火（料面状态）：生产版本（Sprint 3 主场景）
('MV-MATERIAL-LEVEL-20260325-001', 'HEAD-MATERIAL-LEVEL-V1',
 'minio://tianjing-models-prod/sinter/material-level/v1.0.0/model.onnx', 'ONNX',
 NULL, NULL, 0.928, 0.906,
 9.0, 92.0, 42.1,
 'PRODUCTION', 50,
 'USR-TEST-REV-A', 'SYSTEM', 'SYSTEM'),

-- 候选模型（用于 Sandbox 验证 + 四眼原则测试流程）
-- submitted_by = test_reviewer_a，approve 时须用 test_reviewer_b（四眼原则）
('MV-BILLET-CRACK-20260401-002', 'HEAD-BILLET-CRACK-V1',
 'minio://tianjing-models-staging/steel/billet-crack/v1.1.0/model.onnx', 'ONNX',
 0.881, 0.736, 0.906, 0.859,
 36.5, 495.0, 83.2,
 'SANDBOX_VALIDATING', 32,
 'USR-TEST-REV-A', 'SYSTEM', 'SYSTEM');


-- ============================================================
-- PART 5：场景配置（全部 16 个工业视觉检测场景）
--
-- 状态规则（对应测试计划分层测试策略）：
--   ACTIVE        → P1 场景 + Sprint 3 场景（可立即用于集成测试）
--   DRAFT         → P2 / P3 场景（配置完整，尚未上线）
--   SANDBOX_ONLY  → P4 场景（实验室模式，不触发生产告警）
-- ============================================================

-- ---------- P1 场景（5 个，ACTIVE） ----------

-- P1-1：铸坯表面缺陷识别（炼钢厂）
INSERT INTO scene_config (
    scene_id, scene_name, factory_code, process_code, category, priority, status,
    workflow_json, algo_config_json, alarm_config_json, roi_config_json,
    sandbox_enabled, prod_model_id, frame_interval, created_by, updated_by
) VALUES (
    'SCENE-STEEL-001', '铸坯表面缺陷识别', 'STEEL', 'STEEL_CONTINUOUS_CAST',
    'QUALITY_INSPECT', 'P1', 'ACTIVE',
    '{"nodes":[{"id":"vs-1","type":"videoSource","label":"连铸摄像头","params":{"source_type":"replay","fps":10}},{"id":"pp-1","type":"preprocess","label":"图像预处理","params":{"enhance":false,"resize":true}},{"id":"inf-1","type":"inference","label":"铸坯裂纹检测","params":{"plugin_id":"HEAD-BILLET-CRACK-V1","conf_threshold":0.82}},{"id":"alm-1","type":"alarm","label":"告警输出","params":{"level":"CRITICAL","confirm_frames":3}}],"edges":[{"id":"e1","source":"vs-1","target":"pp-1"},{"id":"e2","source":"pp-1","target":"inf-1"},{"id":"e3","source":"inf-1","target":"alm-1"}]}',
    '{"plugin_id":"HEAD-BILLET-CRACK-V1","conf_threshold":0.82,"iou_threshold":0.45,"extra":{"crack_width_threshold_mm":0.5,"surface_coverage":"MULTI_FACE"}}',
    '{"level":"CRITICAL","confirm_frames":3,"suppress_seconds":300,"push_channels":["IIOT_PLATFORM","WECHAT_WORK","WEBSOCKET"]}',
    '{"regions":[{"name":"铸坯检测区","x":100,"y":50,"w":1720,"h":980}],"calibration_id":null}',
    FALSE, 'MV-BILLET-CRACK-20260320-001', 5, 'test_admin', 'test_admin'
);

-- P1-2：链篦机侧板跑偏检测（球团厂）
INSERT INTO scene_config (
    scene_id, scene_name, factory_code, process_code, category, priority, status,
    workflow_json, algo_config_json, alarm_config_json, roi_config_json,
    sandbox_enabled, prod_model_id, frame_interval, created_by, updated_by
) VALUES (
    'SCENE-PELLET-001', '链篦机侧板跑偏检测', 'PELLET', 'PELLET_CHAIN_GRATE',
    'EQUIPMENT_MONITOR', 'P1', 'ACTIVE',
    '{"nodes":[{"id":"vs-1","type":"videoSource","label":"链篦机摄像头","params":{"source_type":"replay","fps":10}},{"id":"pp-1","type":"preprocess","label":"图像预处理","params":{"enhance":false}},{"id":"inf-1","type":"inference","label":"侧板偏移检测","params":{"plugin_id":"HEAD-SIDEPLATE-V1","conf_threshold":0.85}},{"id":"meas-1","type":"measurement","label":"偏移量测量","params":{"plugin_id":"ATOM-MEASURE-SUBPIXEL-V1","scale_mm_per_px":0.42}},{"id":"alm-1","type":"alarm","label":"告警输出","params":{"level":"CRITICAL","confirm_frames":3}}],"edges":[{"id":"e1","source":"vs-1","target":"pp-1"},{"id":"e2","source":"pp-1","target":"inf-1"},{"id":"e3","source":"inf-1","target":"meas-1"},{"id":"e4","source":"meas-1","target":"alm-1"}]}',
    '{"plugin_id":"HEAD-SIDEPLATE-V1","conf_threshold":0.85,"iou_threshold":0.45,"extra":{"deviation_threshold_mm":15,"measure_enabled":true}}',
    '{"level":"CRITICAL","confirm_frames":3,"suppress_seconds":300,"push_channels":["IIOT_PLATFORM","WECHAT_WORK","WEBSOCKET"]}',
    '{"regions":[{"name":"链篦机左侧板","x":0,"y":200,"w":400,"h":680},{"name":"链篦机右侧板","x":1520,"y":200,"w":400,"h":680}],"calibration_id":null}',
    FALSE, 'MV-SIDEPLATE-20260322-001', 3, 'test_admin', 'test_admin'
);

-- P1-3：烧结机壁条脱落检测（烧结厂）
INSERT INTO scene_config (
    scene_id, scene_name, factory_code, process_code, category, priority, status,
    workflow_json, algo_config_json, alarm_config_json, roi_config_json,
    sandbox_enabled, prod_model_id, frame_interval, created_by, updated_by
) VALUES (
    'SCENE-SINTER-001', '烧结机壁条脱落检测', 'SINTER', 'SINTER_SINTERING',
    'EQUIPMENT_MONITOR', 'P1', 'ACTIVE',
    '{"nodes":[{"id":"vs-1","type":"videoSource","label":"烧结机摄像头","params":{"source_type":"replay","fps":10}},{"id":"pp-1","type":"preprocess","label":"图像预处理","params":{"enhance":true}},{"id":"inf-1","type":"inference","label":"壁条检测","params":{"plugin_id":"ATOM-DETECT-YOLO-V1","conf_threshold":0.88}},{"id":"alm-1","type":"alarm","label":"告警输出","params":{"level":"CRITICAL","confirm_frames":3}}],"edges":[{"id":"e1","source":"vs-1","target":"pp-1"},{"id":"e2","source":"pp-1","target":"inf-1"},{"id":"e3","source":"inf-1","target":"alm-1"}]}',
    '{"plugin_id":"ATOM-DETECT-YOLO-V1","conf_threshold":0.88,"iou_threshold":0.45,"extra":{"target_classes":["壁条脱落","壁条堵塞","壁条翘起"]}}',
    '{"level":"CRITICAL","confirm_frames":3,"suppress_seconds":300,"push_channels":["IIOT_PLATFORM","WECHAT_WORK","WEBSOCKET"]}',
    '{"regions":[{"name":"烧结机壁条检测区","x":50,"y":100,"w":1820,"h":880}],"calibration_id":null}',
    FALSE, 'MV-WALLBAR-20260322-001', 5, 'test_admin', 'test_admin'
);

-- P1-4：冷床入口钢材质量检测（型钢厂）
INSERT INTO scene_config (
    scene_id, scene_name, factory_code, process_code, category, priority, status,
    workflow_json, algo_config_json, alarm_config_json, roi_config_json,
    sandbox_enabled, prod_model_id, frame_interval, created_by, updated_by
) VALUES (
    'SCENE-SECTION-001', '冷床入口钢材质量检测', 'SECTION', 'SECTION_COLD_BED',
    'QUALITY_INSPECT', 'P1', 'ACTIVE',
    '{"nodes":[{"id":"vs-1","type":"videoSource","label":"冷床摄像头","params":{"source_type":"replay","fps":10}},{"id":"pp-1","type":"preprocess","label":"图像预处理","params":{"enhance":false}},{"id":"inf-1","type":"inference","label":"钢材表面缺陷检测","params":{"plugin_id":"HEAD-STEEL-SURFACE-V1","conf_threshold":0.85}},{"id":"alm-1","type":"alarm","label":"告警输出","params":{"level":"WARNING","confirm_frames":2}}],"edges":[{"id":"e1","source":"vs-1","target":"pp-1"},{"id":"e2","source":"pp-1","target":"inf-1"},{"id":"e3","source":"inf-1","target":"alm-1"}]}',
    '{"plugin_id":"HEAD-STEEL-SURFACE-V1","conf_threshold":0.85,"iou_threshold":0.45,"extra":{"defect_types":["划伤","折叠","结疤","裂纹"]}}',
    '{"level":"WARNING","confirm_frames":2,"suppress_seconds":180,"push_channels":["IIOT_PLATFORM","WEBSOCKET"]}',
    '{"regions":[{"name":"冷床检测区","x":0,"y":0,"w":1920,"h":1080}],"calibration_id":null}',
    FALSE, 'MV-STEEL-SURFACE-20260323-001', 3, 'test_admin', 'test_admin'
);

-- P1-5：冷床入口钢材尺寸检测（型钢厂）
INSERT INTO scene_config (
    scene_id, scene_name, factory_code, process_code, category, priority, status,
    workflow_json, algo_config_json, alarm_config_json, roi_config_json,
    sandbox_enabled, prod_model_id, frame_interval, created_by, updated_by
) VALUES (
    'SCENE-SECTION-002', '冷床入口钢材尺寸检测', 'SECTION', 'SECTION_COLD_BED',
    'QUALITY_INSPECT', 'P1', 'ACTIVE',
    '{"nodes":[{"id":"vs-1","type":"videoSource","label":"冷床测量摄像头","params":{"source_type":"replay","fps":5}},{"id":"pp-1","type":"preprocess","label":"图像预处理","params":{"enhance":false}},{"id":"meas-1","type":"measurement","label":"尺寸测量","params":{"plugin_id":"HEAD-DIMENSION-V1","scale_mm_per_px":0.5}},{"id":"alm-1","type":"alarm","label":"告警输出","params":{"level":"WARNING","confirm_frames":3}}],"edges":[{"id":"e1","source":"vs-1","target":"pp-1"},{"id":"e2","source":"pp-1","target":"meas-1"},{"id":"e3","source":"meas-1","target":"alm-1"}]}',
    '{"plugin_id":"HEAD-DIMENSION-V1","conf_threshold":0.90,"iou_threshold":0.50,"extra":{"scale_mm_per_px":0.5,"width_nominal_mm":200,"tolerance_mm":3.0}}',
    '{"level":"WARNING","confirm_frames":3,"suppress_seconds":120,"push_channels":["IIOT_PLATFORM","WEBSOCKET"]}',
    '{"regions":[{"name":"尺寸测量区","x":200,"y":400,"w":1520,"h":280}],"calibration_id":null}',
    FALSE, NULL, 5, 'test_admin', 'test_admin'
);

-- ---------- Sprint 3 专用场景（ACTIVE，端到端全链路测试核心场景） ----------

-- Sprint 3 场景：烧结看火（点火强度/料面状态）
INSERT INTO scene_config (
    scene_id, scene_name, factory_code, process_code, category, priority, status,
    workflow_json, algo_config_json, alarm_config_json, roi_config_json,
    sandbox_enabled, prod_model_id, frame_interval, created_by, updated_by
) VALUES (
    'SCENE-SINTER-FIRE-001', '烧结看火场景（点火强度检测）', 'SINTER', 'SINTER_IGNITION',
    'PROCESS_PARAM', 'P3', 'ACTIVE',
    '{"nodes":[{"id":"vs-1","type":"videoSource","label":"点火炉摄像头","params":{"source_type":"replay","fps":10,"minio_path":"tianjing-lab-video/sintering/"}},{"id":"enh-1","type":"preprocess","label":"去雾增强","params":{"plugin_id":"ATOM-ENHANCE-DEHAZE-V1","enhance_level":"MEDIUM","dehaze_enabled":true}},{"id":"inf-1","type":"inference","label":"料面状态识别","params":{"plugin_id":"CLOUD-PROXY-V1","conf_threshold":0.85}},{"id":"alm-1","type":"alarm","label":"告警输出","params":{"level":"WARNING","confirm_frames":3}}],"edges":[{"id":"e1","source":"vs-1","target":"enh-1"},{"id":"e2","source":"enh-1","target":"inf-1"},{"id":"e3","source":"inf-1","target":"alm-1"}]}',
    '{"plugin_id":"CLOUD-PROXY-V1","conf_threshold":0.85,"iou_threshold":0.45,"extra":{"backend_type":"onnx_cpu","roi_enabled":true}}',
    '{"level":"WARNING","confirm_frames":3,"suppress_seconds":300,"push_channels":["IIOT_PLATFORM","WEBSOCKET"]}',
    '{"regions":[{"name":"点火炉料面区","x":50,"y":100,"w":1820,"h":780}],"calibration_id":null}',
    TRUE, 'MV-MATERIAL-LEVEL-20260325-001', 3, 'test_scene_editor', 'test_scene_editor'
);

-- ---------- P2 场景（4 个，DRAFT） ----------

INSERT INTO scene_config (
    scene_id, scene_name, factory_code, process_code, category, priority, status,
    workflow_json, algo_config_json, alarm_config_json, roi_config_json,
    sandbox_enabled, frame_interval, created_by, updated_by
) VALUES
(
    'SCENE-SECTION-003', '检查台架钢材质量检测', 'SECTION', 'SECTION_ROLLING',
    'QUALITY_INSPECT', 'P2', 'DRAFT',
    '{"nodes":[{"id":"vs-1","type":"videoSource","params":{"source_type":"replay"}},{"id":"pp-1","type":"preprocess","params":{"enhance":false}},{"id":"inf-1","type":"inference","params":{"plugin_id":"HEAD-STEEL-SURFACE-V1","conf_threshold":0.85}},{"id":"alm-1","type":"alarm","params":{"level":"WARNING","confirm_frames":2}}],"edges":[{"id":"e1","source":"vs-1","target":"pp-1"},{"id":"e2","source":"pp-1","target":"inf-1"},{"id":"e3","source":"inf-1","target":"alm-1"}]}',
    '{"plugin_id":"HEAD-STEEL-SURFACE-V1","conf_threshold":0.85,"iou_threshold":0.45,"extra":{}}',
    '{"level":"WARNING","confirm_frames":2,"suppress_seconds":180,"push_channels":["IIOT_PLATFORM","WEBSOCKET"]}',
    '{"regions":[{"name":"检查台架区","x":0,"y":0,"w":1920,"h":1080}],"calibration_id":null}',
    FALSE, 3, 'test_scene_editor', 'test_scene_editor'
),
(
    'SCENE-STEEL-002', '加热炉铸坯质量检测', 'SECTION', 'SECTION_HEATING_FURNACE',
    'QUALITY_INSPECT', 'P2', 'DRAFT',
    '{"nodes":[{"id":"vs-1","type":"videoSource","params":{"source_type":"replay"}},{"id":"pp-1","type":"preprocess","params":{"enhance":true}},{"id":"inf-1","type":"inference","params":{"plugin_id":"HEAD-STEEL-SURFACE-V1","conf_threshold":0.85}},{"id":"alm-1","type":"alarm","params":{"level":"WARNING","confirm_frames":2}}],"edges":[{"id":"e1","source":"vs-1","target":"pp-1"},{"id":"e2","source":"pp-1","target":"inf-1"},{"id":"e3","source":"inf-1","target":"alm-1"}]}',
    '{"plugin_id":"HEAD-STEEL-SURFACE-V1","conf_threshold":0.85,"iou_threshold":0.45,"extra":{}}',
    '{"level":"WARNING","confirm_frames":2,"suppress_seconds":180,"push_channels":["IIOT_PLATFORM","WEBSOCKET"]}',
    '{"regions":[{"name":"加热炉出口区","x":0,"y":0,"w":1920,"h":1080}],"calibration_id":null}',
    FALSE, 5, 'test_scene_editor', 'test_scene_editor'
),
(
    'SCENE-PELLET-002', '分料器运行状态检测', 'PELLET', 'PELLET_CHAIN_GRATE',
    'EQUIPMENT_MONITOR', 'P2', 'DRAFT',
    '{"nodes":[{"id":"vs-1","type":"videoSource","params":{"source_type":"replay"}},{"id":"pp-1","type":"preprocess","params":{"enhance":false}},{"id":"inf-1","type":"inference","params":{"plugin_id":"HEAD-FEEDER-STATE-V1","conf_threshold":0.90}},{"id":"alm-1","type":"alarm","params":{"level":"WARNING","confirm_frames":2}}],"edges":[{"id":"e1","source":"vs-1","target":"pp-1"},{"id":"e2","source":"pp-1","target":"inf-1"},{"id":"e3","source":"inf-1","target":"alm-1"}]}',
    '{"plugin_id":"HEAD-FEEDER-STATE-V1","conf_threshold":0.90,"iou_threshold":0.50,"extra":{"alert_on_stop":true}}',
    '{"level":"WARNING","confirm_frames":2,"suppress_seconds":120,"push_channels":["IIOT_PLATFORM","WEBSOCKET"]}',
    '{"regions":[{"name":"分料器区","x":300,"y":200,"w":1320,"h":680}],"calibration_id":null}',
    FALSE, 2, 'test_scene_editor', 'test_scene_editor'
),
(
    'SCENE-SINTER-002', '台车篦条缺损检测', 'SINTER', 'SINTER_SINTERING',
    'EQUIPMENT_MONITOR', 'P2', 'DRAFT',
    '{"nodes":[{"id":"vs-1","type":"videoSource","params":{"source_type":"replay"}},{"id":"pp-1","type":"preprocess","params":{"enhance":false}},{"id":"inf-1","type":"inference","params":{"plugin_id":"HEAD-GRATE-BAR-V1","conf_threshold":0.85}},{"id":"alm-1","type":"alarm","params":{"level":"WARNING","confirm_frames":3}}],"edges":[{"id":"e1","source":"vs-1","target":"pp-1"},{"id":"e2","source":"pp-1","target":"inf-1"},{"id":"e3","source":"inf-1","target":"alm-1"}]}',
    '{"plugin_id":"HEAD-GRATE-BAR-V1","conf_threshold":0.85,"iou_threshold":0.45,"extra":{"min_defect_size_px":12}}',
    '{"level":"WARNING","confirm_frames":3,"suppress_seconds":300,"push_channels":["IIOT_PLATFORM","WEBSOCKET"]}',
    '{"regions":[{"name":"台车篦条区","x":0,"y":200,"w":1920,"h":680}],"calibration_id":null}',
    FALSE, 5, 'test_scene_editor', 'test_scene_editor'
);

-- ---------- P3 场景（4 个，DRAFT） ----------
INSERT INTO scene_config (
    scene_id, scene_name, factory_code, process_code, category, priority, status,
    workflow_json, algo_config_json, alarm_config_json, roi_config_json,
    sandbox_enabled, frame_interval, created_by, updated_by
) VALUES
(
    'SCENE-STRIP-001', '带钢飞剪优化剪切', 'STRIP', 'STRIP_FLYING_SHEAR_AREA',
    'PROCESS_PARAM', 'P3', 'DRAFT',
    '{"nodes":[{"id":"vs-1","type":"videoSource","params":{"source_type":"replay"}},{"id":"pp-1","type":"preprocess","params":{"enhance":false}},{"id":"meas-1","type":"measurement","params":{"plugin_id":"HEAD-DIMENSION-V1","scale_mm_per_px":0.35}},{"id":"alm-1","type":"alarm","params":{"level":"WARNING","confirm_frames":1}}],"edges":[{"id":"e1","source":"vs-1","target":"pp-1"},{"id":"e2","source":"pp-1","target":"meas-1"},{"id":"e3","source":"meas-1","target":"alm-1"}]}',
    '{"plugin_id":"HEAD-DIMENSION-V1","conf_threshold":0.90,"iou_threshold":0.50,"extra":{"scale_mm_per_px":0.35,"width_nominal_mm":1500,"tolerance_mm":5.0}}',
    '{"level":"WARNING","confirm_frames":1,"suppress_seconds":60,"push_channels":["IIOT_PLATFORM"]}',
    '{"regions":[{"name":"飞剪剪切区","x":0,"y":300,"w":1920,"h":480}],"calibration_id":null}',
    FALSE, 1, 'test_scene_editor', 'test_scene_editor'
),
(
    'SCENE-STRIP-002', '卷取端面质检', 'STRIP', 'STRIP_COILING',
    'QUALITY_INSPECT', 'P3', 'DRAFT',
    '{"nodes":[{"id":"vs-1","type":"videoSource","params":{"source_type":"replay"}},{"id":"pp-1","type":"preprocess","params":{"enhance":false}},{"id":"inf-1","type":"inference","params":{"plugin_id":"ATOM-SEGMENT-SAM-V1","conf_threshold":0.80}},{"id":"alm-1","type":"alarm","params":{"level":"WARNING","confirm_frames":2}}],"edges":[{"id":"e1","source":"vs-1","target":"pp-1"},{"id":"e2","source":"pp-1","target":"inf-1"},{"id":"e3","source":"inf-1","target":"alm-1"}]}',
    '{"plugin_id":"ATOM-SEGMENT-SAM-V1","conf_threshold":0.80,"iou_threshold":0.45,"extra":{}}',
    '{"level":"WARNING","confirm_frames":2,"suppress_seconds":180,"push_channels":["IIOT_PLATFORM","WEBSOCKET"]}',
    '{"regions":[{"name":"卷取端面区","x":400,"y":0,"w":1120,"h":1080}],"calibration_id":null}',
    FALSE, 3, 'test_scene_editor', 'test_scene_editor'
),
(
    'SCENE-PELLET-003', '成一皮带料面检测', 'PELLET', 'PELLET_ANNULAR_COOLER',
    'PROCESS_PARAM', 'P3', 'DRAFT',
    '{"nodes":[{"id":"vs-1","type":"videoSource","params":{"source_type":"replay"}},{"id":"pp-1","type":"preprocess","params":{"enhance":false}},{"id":"inf-1","type":"inference","params":{"plugin_id":"HEAD-MATERIAL-LEVEL-V1","conf_threshold":0.88}},{"id":"alm-1","type":"alarm","params":{"level":"INFO","confirm_frames":5}}],"edges":[{"id":"e1","source":"vs-1","target":"pp-1"},{"id":"e2","source":"pp-1","target":"inf-1"},{"id":"e3","source":"inf-1","target":"alm-1"}]}',
    '{"plugin_id":"HEAD-MATERIAL-LEVEL-V1","conf_threshold":0.88,"iou_threshold":0.50,"extra":{"alarm_on_low":true,"alarm_on_high":true}}',
    '{"level":"INFO","confirm_frames":5,"suppress_seconds":600,"push_channels":["WEBSOCKET"]}',
    '{"regions":[{"name":"皮带料面区","x":0,"y":200,"w":1920,"h":680}],"calibration_id":null}',
    FALSE, 5, 'test_scene_editor', 'test_scene_editor'
),
(
    'SCENE-SINTER-003', '台车点火炉料面检测', 'SINTER', 'SINTER_IGNITION',
    'PROCESS_PARAM', 'P3', 'DRAFT',
    '{"nodes":[{"id":"vs-1","type":"videoSource","params":{"source_type":"replay"}},{"id":"pp-1","type":"preprocess","params":{"enhance":false}},{"id":"inf-1","type":"inference","params":{"plugin_id":"HEAD-MATERIAL-LEVEL-V1","conf_threshold":0.88}},{"id":"alm-1","type":"alarm","params":{"level":"INFO","confirm_frames":3}}],"edges":[{"id":"e1","source":"vs-1","target":"pp-1"},{"id":"e2","source":"pp-1","target":"inf-1"},{"id":"e3","source":"inf-1","target":"alm-1"}]}',
    '{"plugin_id":"HEAD-MATERIAL-LEVEL-V1","conf_threshold":0.88,"iou_threshold":0.50,"extra":{"alarm_on_low":true,"alarm_on_high":false}}',
    '{"level":"INFO","confirm_frames":3,"suppress_seconds":600,"push_channels":["WEBSOCKET"]}',
    '{"regions":[{"name":"点火料面区","x":50,"y":100,"w":1820,"h":780}],"calibration_id":null}',
    FALSE, 5, 'test_scene_editor', 'test_scene_editor'
);

-- ---------- P4 场景（3 个，SANDBOX_ONLY，实验室研究阶段） ----------
INSERT INTO scene_config (
    scene_id, scene_name, factory_code, process_code, category, priority, status,
    workflow_json, algo_config_json, alarm_config_json, roi_config_json,
    sandbox_enabled, frame_interval, created_by, updated_by
) VALUES
(
    'SCENE-STEEL-003', '转炉溅渣渣粒密度估计', 'STEEL', 'STEEL_CONVERTER',
    'PROCESS_PARAM', 'P4', 'SANDBOX_ONLY',
    '{"nodes":[{"id":"vs-1","type":"videoSource","params":{"source_type":"replay"}},{"id":"enh-1","type":"preprocess","params":{"plugin_id":"ATOM-ENHANCE-DEHAZE-V1","enhance_level":"HIGH","dehaze_enabled":true}},{"id":"inf-1","type":"inference","params":{"plugin_id":"HEAD-SLAG-DENSITY-V1","conf_threshold":0.85}},{"id":"alm-1","type":"alarm","params":{"level":"INFO","confirm_frames":5}}],"edges":[{"id":"e1","source":"vs-1","target":"enh-1"},{"id":"e2","source":"enh-1","target":"inf-1"},{"id":"e3","source":"inf-1","target":"alm-1"}]}',
    '{"plugin_id":"HEAD-SLAG-DENSITY-V1","conf_threshold":0.85,"iou_threshold":0.50,"extra":{"density_alarm_level":"BOTH_EXTREMES"}}',
    '{"level":"INFO","confirm_frames":5,"suppress_seconds":600,"push_channels":["WEBSOCKET"]}',
    '{"regions":[{"name":"转炉口区","x":400,"y":200,"w":1120,"h":680}],"calibration_id":null}',
    TRUE, 5, 'test_scene_editor', 'test_scene_editor'
),
(
    'SCENE-STEEL-004', '吹氩站底吹处理检测', 'STEEL', 'STEEL_LADLE',
    'PROCESS_PARAM', 'P4', 'SANDBOX_ONLY',
    '{"nodes":[{"id":"vs-1","type":"videoSource","params":{"source_type":"replay"}},{"id":"pp-1","type":"preprocess","params":{"enhance":false}},{"id":"inf-1","type":"inference","params":{"plugin_id":"ATOM-CLASSIFY-RESNET-V1","conf_threshold":0.90}},{"id":"alm-1","type":"alarm","params":{"level":"INFO","confirm_frames":3}}],"edges":[{"id":"e1","source":"vs-1","target":"pp-1"},{"id":"e2","source":"pp-1","target":"inf-1"},{"id":"e3","source":"inf-1","target":"alm-1"}]}',
    '{"plugin_id":"ATOM-CLASSIFY-RESNET-V1","conf_threshold":0.90,"iou_threshold":0.50,"extra":{}}',
    '{"level":"INFO","confirm_frames":3,"suppress_seconds":300,"push_channels":["WEBSOCKET"]}',
    '{"regions":[{"name":"钢包底吹区","x":200,"y":200,"w":1520,"h":680}],"calibration_id":null}',
    TRUE, 5, 'test_scene_editor', 'test_scene_editor'
),
(
    'SCENE-SINTER-004', '白灰仓燃料仓粒度检测', 'SINTER', 'SINTER_COOLING',
    'PROCESS_PARAM', 'P4', 'SANDBOX_ONLY',
    '{"nodes":[{"id":"vs-1","type":"videoSource","params":{"source_type":"replay"}},{"id":"pp-1","type":"preprocess","params":{"enhance":false}},{"id":"inf-1","type":"inference","params":{"plugin_id":"ATOM-DETECT-YOLO-V1","conf_threshold":0.80}},{"id":"alm-1","type":"alarm","params":{"level":"INFO","confirm_frames":5}}],"edges":[{"id":"e1","source":"vs-1","target":"pp-1"},{"id":"e2","source":"pp-1","target":"inf-1"},{"id":"e3","source":"inf-1","target":"alm-1"}]}',
    '{"plugin_id":"ATOM-DETECT-YOLO-V1","conf_threshold":0.80,"iou_threshold":0.45,"extra":{"target_classes":["粒度超标","粒度不均"]}}',
    '{"level":"INFO","confirm_frames":5,"suppress_seconds":1200,"push_channels":["WEBSOCKET"]}',
    '{"regions":[{"name":"粒度检测区","x":0,"y":0,"w":1920,"h":1080}],"calibration_id":null}',
    TRUE, 5, 'test_scene_editor', 'test_scene_editor'
);


-- ============================================================
-- PART 6：摄像头设备（每个场景绑定 1 台）
-- IP 段规则：
--   球团厂 PELLET  → 192.168.10.x
--   烧结厂 SINTER  → 192.168.20.x
--   炼钢厂 STEEL   → 192.168.30.x
--   型钢厂 SECTION → 192.168.40.x
--   带钢厂 STRIP   → 192.168.50.x
-- RTSP URL 字段：使用脱敏占位符，部署时由运维注入真实 URL
-- ============================================================
INSERT INTO camera_device (
    device_code, scene_id, device_name, ip_address, mac_address, vendor,
    firmware_version, protocol, resolution_width, resolution_height,
    rtsp_url, health_status, created_by, updated_by
) VALUES
-- 球团厂
('CAM-PELLET-001', 'SCENE-PELLET-001', '链篦机1#侧板检测相机',  '192.168.10.11', 'A0:B1:C2:D3:E4:01', 'HIKVISION', 'V5.7.2_220929', 'RTSP', 1920, 1080, '{RTSP_URL_PELLET_001}', 'ONLINE',  'test_admin', 'test_admin'),
('CAM-PELLET-002', 'SCENE-PELLET-002', '链篦机分料器状态相机',   '192.168.10.12', 'A0:B1:C2:D3:E4:02', 'HIKVISION', 'V5.7.2_220929', 'RTSP', 1920, 1080, '{RTSP_URL_PELLET_002}', 'ONLINE',  'test_admin', 'test_admin'),
('CAM-PELLET-003', 'SCENE-PELLET-003', '成一皮带料面检测相机',   '192.168.10.13', 'A0:B1:C2:D3:E4:03', 'DAHUA',    'V2.8.1_210630', 'RTSP', 1920, 1080, '{RTSP_URL_PELLET_003}', 'OFFLINE', 'test_admin', 'test_admin'),
-- 烧结厂
('CAM-SINTER-001', 'SCENE-SINTER-001', '烧结机1#壁条检测相机',  '192.168.20.11', 'B0:B1:C2:D3:E4:01', 'HIKVISION', 'V5.7.5_230315', 'RTSP', 1920, 1080, '{RTSP_URL_SINTER_001}', 'ONLINE',  'test_admin', 'test_admin'),
('CAM-SINTER-002', 'SCENE-SINTER-002', '台车篦条检测相机',       '192.168.20.12', 'B0:B1:C2:D3:E4:02', 'HIKVISION', 'V5.7.5_230315', 'RTSP', 1920, 1080, '{RTSP_URL_SINTER_002}', 'ONLINE',  'test_admin', 'test_admin'),
('CAM-SINTER-003', 'SCENE-SINTER-FIRE-001', '点火炉看火相机',   '192.168.20.13', 'B0:B1:C2:D3:E4:03', 'DAHUA',    'V2.9.0_230801', 'RTSP', 1920, 1080, '{RTSP_URL_SINTER_FIRE}', 'ONLINE', 'test_admin', 'test_admin'),
('CAM-SINTER-004', 'SCENE-SINTER-003', '台车点火料面相机',       '192.168.20.14', 'B0:B1:C2:D3:E4:04', 'DAHUA',    'V2.9.0_230801', 'RTSP', 1920, 1080, '{RTSP_URL_SINTER_003}', 'OFFLINE', 'test_admin', 'test_admin'),
('CAM-SINTER-005', 'SCENE-SINTER-004', '白灰仓粒度检测相机',     '192.168.20.15', 'B0:B1:C2:D3:E4:05', 'UNIVIEW',  'V3.1.0_231201', 'RTSP', 1920, 1080, '{RTSP_URL_SINTER_004}', 'OFFLINE', 'test_admin', 'test_admin'),
-- 炼钢厂
('CAM-STEEL-001',  'SCENE-STEEL-001',  '连铸1#铸坯表面检测相机', '192.168.30.11', 'C0:B1:C2:D3:E4:01', 'HIKVISION', 'V5.8.0_231010', 'RTSP', 1920, 1080, '{RTSP_URL_STEEL_001}',  'ONLINE',  'test_admin', 'test_admin'),
('CAM-STEEL-002',  'SCENE-STEEL-002',  '加热炉铸坯质量相机',     '192.168.30.12', 'C0:B1:C2:D3:E4:02', 'HIKVISION', 'V5.8.0_231010', 'RTSP', 1920, 1080, '{RTSP_URL_STEEL_002}',  'OFFLINE', 'test_admin', 'test_admin'),
('CAM-STEEL-003',  'SCENE-STEEL-003',  '转炉溅渣密度相机',       '192.168.30.13', 'C0:B1:C2:D3:E4:03', 'DAHUA',    'V2.8.1_210630', 'RTSP', 1920, 1080, '{RTSP_URL_STEEL_003}',  'OFFLINE', 'test_admin', 'test_admin'),
('CAM-STEEL-004',  'SCENE-STEEL-004',  '吹氩站底吹检测相机',     '192.168.30.14', 'C0:B1:C2:D3:E4:04', 'DAHUA',    'V2.8.1_210630', 'RTSP', 1920, 1080, '{RTSP_URL_STEEL_004}',  'OFFLINE', 'test_admin', 'test_admin'),
-- 型钢厂
('CAM-SECTION-001','SCENE-SECTION-001','冷床入口质量检测相机',   '192.168.40.11', 'D0:B1:C2:D3:E4:01', 'HIKVISION', 'V5.7.5_230315', 'RTSP', 1920, 1080, '{RTSP_URL_SECTION_001}','ONLINE',  'test_admin', 'test_admin'),
('CAM-SECTION-002','SCENE-SECTION-002','冷床入口尺寸测量相机',   '192.168.40.12', 'D0:B1:C2:D3:E4:02', 'HIKVISION', 'V5.7.5_230315', 'RTSP', 1920, 1080, '{RTSP_URL_SECTION_002}','ONLINE',  'test_admin', 'test_admin'),
('CAM-SECTION-003','SCENE-SECTION-003','检查台架质量检测相机',   '192.168.40.13', 'D0:B1:C2:D3:E4:03', 'UNIVIEW',  'V3.1.0_231201', 'RTSP', 1920, 1080, '{RTSP_URL_SECTION_003}','OFFLINE', 'test_admin', 'test_admin'),
-- 带钢厂
('CAM-STRIP-001',  'SCENE-STRIP-001',  '带钢飞剪剪切检测相机',  '192.168.50.11', 'E0:B1:C2:D3:E4:01', 'HIKVISION', 'V5.8.0_231010', 'RTSP', 1920, 1080, '{RTSP_URL_STRIP_001}',  'OFFLINE', 'test_admin', 'test_admin'),
('CAM-STRIP-002',  'SCENE-STRIP-002',  '卷取端面质检相机',      '192.168.50.12', 'E0:B1:C2:D3:E4:02', 'DAHUA',    'V2.9.0_230801', 'RTSP', 1920, 1080, '{RTSP_URL_STRIP_002}',  'OFFLINE', 'test_admin', 'test_admin');


-- ============================================================
-- PART 7：告警规则（每个场景一套规则）
-- ============================================================
INSERT INTO alarm_rule (
    rule_id, scene_id, alarm_level, conf_threshold,
    confirm_frames, suppress_seconds, push_channels,
    scada_signal, init_mode, created_by, updated_by
) VALUES
-- P1 场景（CRITICAL，宽通道推送）
('RULE-SCENE-STEEL-001',       'SCENE-STEEL-001',       'CRITICAL', 0.82, 3, 300, ARRAY['IIOT_PLATFORM','WECHAT_WORK','WEBSOCKET'], FALSE, 'SUGGEST', 'test_admin', 'test_admin'),
('RULE-SCENE-PELLET-001',      'SCENE-PELLET-001',      'CRITICAL', 0.85, 3, 300, ARRAY['IIOT_PLATFORM','WECHAT_WORK','WEBSOCKET'], FALSE, 'SUGGEST', 'test_admin', 'test_admin'),
('RULE-SCENE-SINTER-001',      'SCENE-SINTER-001',      'CRITICAL', 0.88, 3, 300, ARRAY['IIOT_PLATFORM','WECHAT_WORK','WEBSOCKET'], FALSE, 'SUGGEST', 'test_admin', 'test_admin'),
('RULE-SCENE-SECTION-001',     'SCENE-SECTION-001',     'WARNING',  0.85, 2, 180, ARRAY['IIOT_PLATFORM','WEBSOCKET'],              FALSE, 'SUGGEST', 'test_admin', 'test_admin'),
('RULE-SCENE-SECTION-002',     'SCENE-SECTION-002',     'WARNING',  0.90, 3, 120, ARRAY['IIOT_PLATFORM','WEBSOCKET'],              FALSE, 'SUGGEST', 'test_admin', 'test_admin'),
-- Sprint 3 场景
('RULE-SCENE-SINTER-FIRE-001', 'SCENE-SINTER-FIRE-001', 'WARNING',  0.85, 3, 300, ARRAY['IIOT_PLATFORM','WEBSOCKET'],              FALSE, 'SUGGEST', 'test_admin', 'test_admin'),
-- P2 场景（WARNING，部分通道）
('RULE-SCENE-SECTION-003',     'SCENE-SECTION-003',     'WARNING',  0.85, 2, 180, ARRAY['IIOT_PLATFORM','WEBSOCKET'],              FALSE, 'SUGGEST', 'test_admin', 'test_admin'),
('RULE-SCENE-STEEL-002',       'SCENE-STEEL-002',       'WARNING',  0.85, 2, 180, ARRAY['IIOT_PLATFORM','WEBSOCKET'],              FALSE, 'SUGGEST', 'test_admin', 'test_admin'),
('RULE-SCENE-PELLET-002',      'SCENE-PELLET-002',      'WARNING',  0.90, 2, 120, ARRAY['IIOT_PLATFORM','WEBSOCKET'],              FALSE, 'SUGGEST', 'test_admin', 'test_admin'),
('RULE-SCENE-SINTER-002',      'SCENE-SINTER-002',      'WARNING',  0.85, 3, 300, ARRAY['IIOT_PLATFORM','WEBSOCKET'],              FALSE, 'SUGGEST', 'test_admin', 'test_admin'),
-- P3 场景（INFO/WARNING，仅 WebSocket）
('RULE-SCENE-STRIP-001',       'SCENE-STRIP-001',       'WARNING',  0.90, 1,  60, ARRAY['IIOT_PLATFORM'],                          FALSE, 'SUGGEST', 'test_admin', 'test_admin'),
('RULE-SCENE-STRIP-002',       'SCENE-STRIP-002',       'WARNING',  0.80, 2, 180, ARRAY['IIOT_PLATFORM','WEBSOCKET'],              FALSE, 'SUGGEST', 'test_admin', 'test_admin'),
('RULE-SCENE-PELLET-003',      'SCENE-PELLET-003',      'INFO',     0.88, 5, 600, ARRAY['WEBSOCKET'],                              FALSE, 'SUGGEST', 'test_admin', 'test_admin'),
('RULE-SCENE-SINTER-003',      'SCENE-SINTER-003',      'INFO',     0.88, 3, 600, ARRAY['WEBSOCKET'],                              FALSE, 'SUGGEST', 'test_admin', 'test_admin'),
-- P4 场景（INFO，仅 WebSocket，沙箱模式不触发生产告警）
('RULE-SCENE-STEEL-003',       'SCENE-STEEL-003',       'INFO',     0.85, 5, 600, ARRAY['WEBSOCKET'],                              FALSE, 'SUGGEST', 'test_admin', 'test_admin'),
('RULE-SCENE-STEEL-004',       'SCENE-STEEL-004',       'INFO',     0.90, 3, 300, ARRAY['WEBSOCKET'],                              FALSE, 'SUGGEST', 'test_admin', 'test_admin'),
('RULE-SCENE-SINTER-004',      'SCENE-SINTER-004',      'INFO',     0.80, 5,1200, ARRAY['WEBSOCKET'],                              FALSE, 'SUGGEST', 'test_admin', 'test_admin');


-- ============================================================
-- PART 8：标定记录（ACTIVE 场景初始标定数据）
-- ============================================================
INSERT INTO calibration_record (
    calibration_id, scene_id, device_code,
    ref_distance_mm, pixel_distance, scale_mm_per_px,
    calibration_image_url, is_active, created_by, updated_by
) VALUES
('CAL-STEEL-001-001',       'SCENE-STEEL-001',       'CAM-STEEL-001',   200.0, 468, 0.4274, 'minio://tianjing-frames-test/calibration/steel-001-calib.jpg',       TRUE, 'test_scene_editor', 'test_scene_editor'),
('CAL-PELLET-001-001',      'SCENE-PELLET-001',      'CAM-PELLET-001',  250.0, 595, 0.4202, 'minio://tianjing-frames-test/calibration/pellet-001-calib.jpg',     TRUE, 'test_scene_editor', 'test_scene_editor'),
('CAL-SINTER-001-001',      'SCENE-SINTER-001',      'CAM-SINTER-001',  300.0, 688, 0.4360, 'minio://tianjing-frames-test/calibration/sinter-001-calib.jpg',     TRUE, 'test_scene_editor', 'test_scene_editor'),
('CAL-SECTION-001-001',     'SCENE-SECTION-001',     'CAM-SECTION-001', 500.0,1002, 0.4990, 'minio://tianjing-frames-test/calibration/section-001-calib.jpg',   TRUE, 'test_scene_editor', 'test_scene_editor'),
('CAL-SECTION-002-001',     'SCENE-SECTION-002',     'CAM-SECTION-002', 500.0,1000, 0.5000, 'minio://tianjing-frames-test/calibration/section-002-calib.jpg',   TRUE, 'test_scene_editor', 'test_scene_editor'),
('CAL-SINTER-FIRE-001-001', 'SCENE-SINTER-FIRE-001', 'CAM-SINTER-003',  200.0, 451, 0.4435, 'minio://tianjing-frames-test/calibration/sinter-fire-001-calib.jpg', TRUE, 'test_scene_editor', 'test_scene_editor');


-- ============================================================
-- PART 9：操作审计日志（初始化操作记录，用于审计查询测试）
-- ============================================================
INSERT INTO sys_operation_log (
    operation, object_type, object_id, operator, result, trace_id, operated_at
) VALUES
('SCENE_CREATE',  'SCENE_CONFIG', 'SCENE-STEEL-001',       'test_admin',        'SUCCESS', 'trace-init-0001', '2026-04-01 09:00:00+08'),
('SCENE_ENABLE',  'SCENE_CONFIG', 'SCENE-STEEL-001',       'test_admin',        'SUCCESS', 'trace-init-0002', '2026-04-01 09:05:00+08'),
('SCENE_CREATE',  'SCENE_CONFIG', 'SCENE-PELLET-001',      'test_admin',        'SUCCESS', 'trace-init-0003', '2026-04-01 09:10:00+08'),
('SCENE_ENABLE',  'SCENE_CONFIG', 'SCENE-PELLET-001',      'test_admin',        'SUCCESS', 'trace-init-0004', '2026-04-01 09:15:00+08'),
('SCENE_CREATE',  'SCENE_CONFIG', 'SCENE-SINTER-001',      'test_admin',        'SUCCESS', 'trace-init-0005', '2026-04-01 09:20:00+08'),
('SCENE_ENABLE',  'SCENE_CONFIG', 'SCENE-SINTER-001',      'test_admin',        'SUCCESS', 'trace-init-0006', '2026-04-01 09:25:00+08'),
('SCENE_CREATE',  'SCENE_CONFIG', 'SCENE-SECTION-001',     'test_admin',        'SUCCESS', 'trace-init-0007', '2026-04-01 09:30:00+08'),
('SCENE_ENABLE',  'SCENE_CONFIG', 'SCENE-SECTION-001',     'test_admin',        'SUCCESS', 'trace-init-0008', '2026-04-01 09:35:00+08'),
('SCENE_CREATE',  'SCENE_CONFIG', 'SCENE-SINTER-FIRE-001', 'test_scene_editor', 'SUCCESS', 'trace-init-0009', '2026-04-01 10:00:00+08'),
('SCENE_ENABLE',  'SCENE_CONFIG', 'SCENE-SINTER-FIRE-001', 'test_scene_editor', 'SUCCESS', 'trace-init-0010', '2026-04-01 10:05:00+08'),
('MODEL_DEPLOY',  'MODEL_VERSION','MV-BILLET-CRACK-20260320-001',  'test_reviewer_b', 'SUCCESS', 'trace-init-0011', '2026-04-01 10:30:00+08'),
('MODEL_APPROVE', 'MODEL_VERSION','MV-WALLBAR-20260322-001',       'test_reviewer_b', 'SUCCESS', 'trace-init-0012', '2026-04-01 10:35:00+08'),
('SANDBOX_PROMOTE', 'MODEL_VERSION','MV-MATERIAL-LEVEL-20260325-001', 'test_reviewer_b', 'SUCCESS', 'trace-init-0013', '2026-04-01 11:00:00+08');
