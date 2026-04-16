-- V8: 场景算法插件精准化匹配
-- 将 6 个使用通用插件的场景升级为对应专用算法头
-- 同步更新 algo_config_json.plugin_id 与 workflow_json 推理节点 plugin_id

-- ─────────────────────────────────────────────────────────────────
-- 1. SCENE-SINTER-001  烧结机壁条脱落检测
--    ATOM-DETECT-YOLO-V1（通用检测） → HEAD-SINTER-GRATE-V1（烧结壁条异常检测头）
-- ─────────────────────────────────────────────────────────────────
UPDATE scene_config
SET
    algo_config_json = jsonb_set(algo_config_json, '{plugin_id}', '"HEAD-SINTER-GRATE-V1"'),
    workflow_json = (
        SELECT jsonb_build_object(
            'nodes', jsonb_agg(
                CASE WHEN node->>'type' = 'inference'
                    THEN jsonb_set(node, '{params,plugin_id}', '"HEAD-SINTER-GRATE-V1"')
                    ELSE node
                END
            ),
            'edges', workflow_json->'edges'
        )
        FROM jsonb_array_elements(workflow_json->'nodes') node
    ),
    updated_at = NOW(),
    updated_by = 'system_migration_v8',
    version = version + 1
WHERE scene_id = 'SCENE-SINTER-001' AND is_deleted = false;

-- ─────────────────────────────────────────────────────────────────
-- 2. SCENE-SINTER-004  白灰仓燃料仓粒度检测
--    ATOM-DETECT-YOLO-V1（通用检测） → HEAD-SINTER-PARTICLE-V1（物料粒度分割检测头）
-- ─────────────────────────────────────────────────────────────────
UPDATE scene_config
SET
    algo_config_json = jsonb_set(algo_config_json, '{plugin_id}', '"HEAD-SINTER-PARTICLE-V1"'),
    workflow_json = (
        SELECT jsonb_build_object(
            'nodes', jsonb_agg(
                CASE WHEN node->>'type' = 'inference'
                    THEN jsonb_set(node, '{params,plugin_id}', '"HEAD-SINTER-PARTICLE-V1"')
                    ELSE node
                END
            ),
            'edges', workflow_json->'edges'
        )
        FROM jsonb_array_elements(workflow_json->'nodes') node
    ),
    updated_at = NOW(),
    updated_by = 'system_migration_v8',
    version = version + 1
WHERE scene_id = 'SCENE-SINTER-004' AND is_deleted = false;

-- ─────────────────────────────────────────────────────────────────
-- 3. SCENE-STEEL-002  加热炉铸坯质量检测（factory_code=SECTION）
--    HEAD-STEEL-SURFACE-V1（通用表面缺陷） → HEAD-SECTION-BILLET-V1（型钢铸坯质量检测头）
-- ─────────────────────────────────────────────────────────────────
UPDATE scene_config
SET
    algo_config_json = jsonb_set(algo_config_json, '{plugin_id}', '"HEAD-SECTION-BILLET-V1"'),
    workflow_json = (
        SELECT jsonb_build_object(
            'nodes', jsonb_agg(
                CASE WHEN node->>'type' = 'inference'
                    THEN jsonb_set(node, '{params,plugin_id}', '"HEAD-SECTION-BILLET-V1"')
                    ELSE node
                END
            ),
            'edges', workflow_json->'edges'
        )
        FROM jsonb_array_elements(workflow_json->'nodes') node
    ),
    updated_at = NOW(),
    updated_by = 'system_migration_v8',
    version = version + 1
WHERE scene_id = 'SCENE-STEEL-002' AND is_deleted = false;

-- ─────────────────────────────────────────────────────────────────
-- 4. SCENE-STEEL-004  吹氩站底吹处理检测
--    ATOM-CLASSIFY-RESNET-V1（通用分类） → HEAD-STEEL-ARGON-V1（转炉底吹处理检测头）
-- ─────────────────────────────────────────────────────────────────
UPDATE scene_config
SET
    algo_config_json = jsonb_set(algo_config_json, '{plugin_id}', '"HEAD-STEEL-ARGON-V1"'),
    workflow_json = (
        SELECT jsonb_build_object(
            'nodes', jsonb_agg(
                CASE WHEN node->>'type' = 'inference'
                    THEN jsonb_set(node, '{params,plugin_id}', '"HEAD-STEEL-ARGON-V1"')
                    ELSE node
                END
            ),
            'edges', workflow_json->'edges'
        )
        FROM jsonb_array_elements(workflow_json->'nodes') node
    ),
    updated_at = NOW(),
    updated_by = 'system_migration_v8',
    version = version + 1
WHERE scene_id = 'SCENE-STEEL-004' AND is_deleted = false;

-- ─────────────────────────────────────────────────────────────────
-- 5. SCENE-STRIP-001  带钢飞剪优化剪切
--    HEAD-DIMENSION-V1（尺寸测量） → HEAD-STRIP-SHEAR-V1（带钢飞剪关键点检测头）
--    同时将 workflow 中的 measurement 节点改为 inference 节点
-- ─────────────────────────────────────────────────────────────────
UPDATE scene_config
SET
    algo_config_json = jsonb_set(
        jsonb_set(algo_config_json, '{plugin_id}', '"HEAD-STRIP-SHEAR-V1"'),
        '{conf_threshold}', '0.88'
    ),
    workflow_json = (
        SELECT jsonb_build_object(
            'nodes', jsonb_agg(
                CASE WHEN node->>'type' = 'measurement'
                    THEN jsonb_build_object(
                        'id',     node->>'id',
                        'type',   'inference',
                        'label',  '飞剪关键点检测',
                        'params', jsonb_build_object(
                            'plugin_id',      'HEAD-STRIP-SHEAR-V1',
                            'conf_threshold', 0.88
                        )
                    )
                    ELSE node
                END
            ),
            'edges', workflow_json->'edges'
        )
        FROM jsonb_array_elements(workflow_json->'nodes') node
    ),
    updated_at = NOW(),
    updated_by = 'system_migration_v8',
    version = version + 1
WHERE scene_id = 'SCENE-STRIP-001' AND is_deleted = false;

-- ─────────────────────────────────────────────────────────────────
-- 6. SCENE-STRIP-002  卷取端面质检
--    ATOM-SEGMENT-SAM-V1（通用分割） → HEAD-STRIP-COIL-V1（带钢卷取端面质检头）
-- ─────────────────────────────────────────────────────────────────
UPDATE scene_config
SET
    algo_config_json = jsonb_set(algo_config_json, '{plugin_id}', '"HEAD-STRIP-COIL-V1"'),
    workflow_json = (
        SELECT jsonb_build_object(
            'nodes', jsonb_agg(
                CASE WHEN node->>'type' = 'inference'
                    THEN jsonb_set(node, '{params,plugin_id}', '"HEAD-STRIP-COIL-V1"')
                    ELSE node
                END
            ),
            'edges', workflow_json->'edges'
        )
        FROM jsonb_array_elements(workflow_json->'nodes') node
    ),
    updated_at = NOW(),
    updated_by = 'system_migration_v8',
    version = version + 1
WHERE scene_id = 'SCENE-STRIP-002' AND is_deleted = false;
