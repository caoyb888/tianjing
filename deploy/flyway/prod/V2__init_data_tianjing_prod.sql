-- ============================================================
-- Flyway 迁移脚本 V2
-- 数据库：tianjing_prod（生产库）
-- Sprint：S0-03
-- 说明：生产库初始化数据（厂部、工序、角色、管理员账号）
-- ============================================================

-- ============================================================
-- 初始化五大厂部
-- ============================================================
INSERT INTO factory (factory_code, factory_name, factory_type, sort_order, created_by, updated_by) VALUES
    ('PELLET',  '球团厂', 'RAW_MATERIAL', 1, 'SYSTEM', 'SYSTEM'),
    ('SINTER',  '烧结厂', 'RAW_MATERIAL', 2, 'SYSTEM', 'SYSTEM'),
    ('STEEL',   '炼钢厂', 'SMELTING',     3, 'SYSTEM', 'SYSTEM'),
    ('SECTION', '型钢厂', 'ROLLING',      4, 'SYSTEM', 'SYSTEM'),
    ('STRIP',   '带钢厂', 'ROLLING',      5, 'SYSTEM', 'SYSTEM');

-- ============================================================
-- 初始化各厂部工序
-- ============================================================
INSERT INTO process (process_code, process_name, factory_code, sort_order, created_by, updated_by) VALUES
    -- 球团厂工序
    ('PELLET_CHAIN_GRATE',    '链篦机工序',     'PELLET',  1, 'SYSTEM', 'SYSTEM'),
    ('PELLET_ROTARY_KILN',    '回转窑工序',     'PELLET',  2, 'SYSTEM', 'SYSTEM'),
    ('PELLET_ANNULAR_COOLER', '环冷机工序',     'PELLET',  3, 'SYSTEM', 'SYSTEM'),
    -- 烧结厂工序
    ('SINTER_SINTERING',      '烧结机工序',     'SINTER',  1, 'SYSTEM', 'SYSTEM'),
    ('SINTER_IGNITION',       '点火炉工序',     'SINTER',  2, 'SYSTEM', 'SYSTEM'),
    ('SINTER_COOLING',        '冷却机工序',     'SINTER',  3, 'SYSTEM', 'SYSTEM'),
    -- 炼钢厂工序
    ('STEEL_CONVERTER',       '转炉车间',       'STEEL',   1, 'SYSTEM', 'SYSTEM'),
    ('STEEL_CONTINUOUS_CAST', '连铸车间',       'STEEL',   2, 'SYSTEM', 'SYSTEM'),
    ('STEEL_LADLE',           '精炼车间',       'STEEL',   3, 'SYSTEM', 'SYSTEM'),
    -- 型钢厂工序
    ('SECTION_ROLLING',       '型钢轧制工序',   'SECTION', 1, 'SYSTEM', 'SYSTEM'),
    ('SECTION_FLYING_SHEAR',  '飞剪工序',       'SECTION', 2, 'SYSTEM', 'SYSTEM'),
    -- 带钢厂工序
    ('STRIP_HOT_ROLLING',     '热轧工序',       'STRIP',   1, 'SYSTEM', 'SYSTEM'),
    ('STRIP_COLD_ROLLING',    '冷轧工序',       'STRIP',   2, 'SYSTEM', 'SYSTEM');

-- ============================================================
-- 初始化五个系统角色
-- ============================================================
INSERT INTO sys_role (role_code, role_name, description, created_by) VALUES
    ('ADMIN',             '超级管理员',   '系统最高权限，可管理用户、角色、场景配置、告警规则等所有功能',       'SYSTEM'),
    ('SCENE_EDITOR',      '场景编辑员',   '负责场景配置管理和低代码编排，可上下线场景，但不能审核模型',         'SYSTEM'),
    ('MODEL_REVIEWER',    '模型审核员',   '负责审核模型版本，批准 Sandbox 转正。四眼原则：不能审核自己提交的模型', 'SYSTEM'),
    ('SANDBOX_OPERATOR',  '实验室操作员', '负责 Sandbox 实验室管理，可开启/停止 Sandbox 会话和查看对比报告',   'SYSTEM'),
    ('VIEWER',            '只读查看者',   '只读权限，可查看看板、告警、场景配置，不可执行任何写操作',           'SYSTEM');

-- ============================================================
-- 初始化超级管理员账号（密码须在首次登录后修改）
-- 密码：Admin@Tianjing2026（BCrypt cost=12 哈希值，实际哈希由运维人员生成后替换）
-- 占位：此处使用明确标识的占位哈希，CI/CD 部署时通过 Secret 注入真实哈希
-- ============================================================
INSERT INTO sys_user (user_id, username, display_name, password_hash, dept_code, created_by, updated_by)
VALUES (
    'USR-ADMIN-001',
    'admin',
    '系统管理员',
    '{PLACEHOLDER_REPLACE_BEFORE_PROD}',   -- SECURITY: 部署前必须替换为真实 BCrypt 哈希
    'IT',
    'SYSTEM',
    'SYSTEM'
);

-- 为管理员分配 ADMIN 角色
INSERT INTO sys_user_role (user_id, role_code, granted_by)
VALUES ('USR-ADMIN-001', 'ADMIN', 'SYSTEM');

-- ============================================================
-- 初始化云端推理代理算法插件（V2.0 使能组件，实验室阶段默认插件）
-- ============================================================
INSERT INTO algorithm_plugin (
    plugin_id, plugin_name, plugin_type, is_atom, version,
    infer_backend, status,
    metadata_json, ui_schema_json,
    created_by, updated_by
) VALUES (
    'CLOUD-PROXY-V1',
    '云端推理代理',
    'DETECTION',
    TRUE,
    '1.0.0',
    'CLOUD_API',
    'ACTIVE',
    '{
        "supported_scenes": ["ALL"],
        "hardware_requirements": {"min_gpu_vram_gb": 0, "supports_tensorrt": false, "supports_onnx": true},
        "accuracy_metrics": {"map50": null, "map50_95": null, "inference_ms_gpu": null, "inference_ms_cpu": 500},
        "description": "实验室阶段云端推理代理，对接公共云视觉 API（阿里云/百度EasyDL）或 CPU ONNX Runtime。GPU 到货后透明切换至本地 GPU 推理，无需修改代码。"
    }',
    '{
        "properties": {
            "conf_threshold": {
                "type": "number", "title": "置信度阈值",
                "minimum": 0.1, "maximum": 1.0, "default": 0.85,
                "ui:widget": "slider"
            },
            "roi_enabled": {
                "type": "boolean", "title": "启用ROI区域",
                "default": true,
                "ui:widget": "switch"
            },
            "backend_type": {
                "type": "string", "title": "推理后端",
                "enum": ["cloud_api", "onnx_cpu"],
                "default": "onnx_cpu",
                "ui:widget": "select"
            }
        },
        "required": ["conf_threshold"]
    }',
    'SYSTEM',
    'SYSTEM'
);
