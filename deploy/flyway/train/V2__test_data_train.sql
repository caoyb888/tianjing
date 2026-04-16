-- ============================================================
-- Flyway 迁移脚本 V2
-- 数据库：tianjing_train（训练库）
-- 说明：永久测试数据集 — 训练管理模块（数据集、版本、训练作业、模型产物）
--       覆盖 P1 核心场景 + Sprint 3 场景（烧结看火），共 5 个数据集
--       对应：测试计划 V1.0 § 5（训练管理功能测试）
-- 编制：测试负责人 · 2026-04-02
-- ============================================================

-- ============================================================
-- PART 1：数据集（5 个，对应 P1 场景 + Sprint 3 场景）
-- 实验室阶段主要数据来源：LAB_REPLAY（录像回放标注）
-- ============================================================
INSERT INTO dataset (
    dataset_code, dataset_name, scene_id, factory_code,
    source_type, total_samples, positive_samples, negative_samples,
    minio_prefix, created_by, updated_by
) VALUES

-- 铸坯裂纹检测数据集（炼钢厂 · SCENE-STEEL-001）
(
    'DS-BILLET-CRACK-001',
    '铸坯裂纹检测数据集',
    'SCENE-STEEL-001', 'STEEL',
    'LAB_REPLAY',
    2350, 470, 1880,
    'tianjing-datasets/steel/SCENE-STEEL-001/billet-crack/',
    'SYSTEM', 'SYSTEM'
),

-- 链篦机侧板跑偏检测数据集（球团厂 · SCENE-PELLET-001）
(
    'DS-SIDEPLATE-001',
    '链篦机侧板跑偏检测数据集',
    'SCENE-PELLET-001', 'PELLET',
    'LAB_REPLAY',
    1920, 384, 1536,
    'tianjing-datasets/pellet/SCENE-PELLET-001/sideplate/',
    'SYSTEM', 'SYSTEM'
),

-- 烧结机壁条脱落检测数据集（烧结厂 · SCENE-SINTER-001）
(
    'DS-WALLBAR-001',
    '烧结机壁条脱落检测数据集',
    'SCENE-SINTER-001', 'SINTER',
    'LAB_REPLAY',
    1640, 328, 1312,
    'tianjing-datasets/sinter/SCENE-SINTER-001/wallbar/',
    'SYSTEM', 'SYSTEM'
),

-- 钢材表面缺陷检测数据集（型钢厂 · 覆盖冷床入口 + 检查台架）
(
    'DS-STEEL-SURFACE-001',
    '型钢冷床钢材表面缺陷数据集',
    'SCENE-SECTION-001', 'SECTION',
    'LAB_REPLAY',
    2480, 620, 1860,
    'tianjing-datasets/section/SCENE-SECTION-001/steel-surface/',
    'SYSTEM', 'SYSTEM'
),

-- 烧结看火料面状态数据集（烧结厂 · SCENE-SINTER-FIRE-001，Sprint 3 主场景）
(
    'DS-MATERIAL-LEVEL-001',
    '烧结看火料面状态数据集',
    'SCENE-SINTER-FIRE-001', 'SINTER',
    'LAB_REPLAY',
    1380, 276, 1104,
    'tianjing-datasets/sinter/SCENE-SINTER-FIRE-001/material-level/',
    'SYSTEM', 'SYSTEM'
);


-- ============================================================
-- PART 2：数据集版本
-- 每个数据集含两个版本：
--   v1.0 → 已冻结（已被训练作业引用），对应当前生产模型训练集
--   v1.1 → 未冻结（扩充样本后的增量版本），用于下次训练
-- ============================================================

-- ---------- DS-BILLET-CRACK-001 ----------

INSERT INTO dataset_version (
    version_id, dataset_code, version_tag, sample_count, minio_prefix,
    split_train, split_val, split_test, is_frozen, created_by
) VALUES
-- v1.0：用于训练 MV-BILLET-CRACK-20260320-001（当前生产版本）
(
    'DV-BILLET-CRACK-V1-0', 'DS-BILLET-CRACK-001', 'v1.0',
    2000, 'tianjing-datasets/steel/SCENE-STEEL-001/billet-crack/v1.0/',
    0.8000, 0.1500, 0.0500, TRUE, 'SYSTEM'
),
-- v1.1：样本扩充至 2350，用于训练候选模型 MV-BILLET-CRACK-20260401-002
(
    'DV-BILLET-CRACK-V1-1', 'DS-BILLET-CRACK-001', 'v1.1',
    2350, 'tianjing-datasets/steel/SCENE-STEEL-001/billet-crack/v1.1/',
    0.8000, 0.1500, 0.0500, TRUE, 'SYSTEM'
);

-- ---------- DS-SIDEPLATE-001 ----------

INSERT INTO dataset_version (
    version_id, dataset_code, version_tag, sample_count, minio_prefix,
    split_train, split_val, split_test, is_frozen, created_by
) VALUES
(
    'DV-SIDEPLATE-V1-0', 'DS-SIDEPLATE-001', 'v1.0',
    1920, 'tianjing-datasets/pellet/SCENE-PELLET-001/sideplate/v1.0/',
    0.8000, 0.1500, 0.0500, TRUE, 'SYSTEM'
),
(
    'DV-SIDEPLATE-V1-1', 'DS-SIDEPLATE-001', 'v1.1',
    1920, 'tianjing-datasets/pellet/SCENE-PELLET-001/sideplate/v1.1/',
    0.8000, 0.1500, 0.0500, FALSE, 'SYSTEM'
);

-- ---------- DS-WALLBAR-001 ----------

INSERT INTO dataset_version (
    version_id, dataset_code, version_tag, sample_count, minio_prefix,
    split_train, split_val, split_test, is_frozen, created_by
) VALUES
(
    'DV-WALLBAR-V1-0', 'DS-WALLBAR-001', 'v1.0',
    1640, 'tianjing-datasets/sinter/SCENE-SINTER-001/wallbar/v1.0/',
    0.8000, 0.1500, 0.0500, TRUE, 'SYSTEM'
),
(
    'DV-WALLBAR-V1-1', 'DS-WALLBAR-001', 'v1.1',
    1640, 'tianjing-datasets/sinter/SCENE-SINTER-001/wallbar/v1.1/',
    0.8000, 0.1500, 0.0500, FALSE, 'SYSTEM'
);

-- ---------- DS-STEEL-SURFACE-001 ----------

INSERT INTO dataset_version (
    version_id, dataset_code, version_tag, sample_count, minio_prefix,
    split_train, split_val, split_test, is_frozen, created_by
) VALUES
(
    'DV-STEEL-SURFACE-V1-0', 'DS-STEEL-SURFACE-001', 'v1.0',
    2480, 'tianjing-datasets/section/SCENE-SECTION-001/steel-surface/v1.0/',
    0.8000, 0.1500, 0.0500, TRUE, 'SYSTEM'
),
(
    'DV-STEEL-SURFACE-V1-1', 'DS-STEEL-SURFACE-001', 'v1.1',
    2480, 'tianjing-datasets/section/SCENE-SECTION-001/steel-surface/v1.1/',
    0.8000, 0.1500, 0.0500, FALSE, 'SYSTEM'
);

-- ---------- DS-MATERIAL-LEVEL-001 ----------

INSERT INTO dataset_version (
    version_id, dataset_code, version_tag, sample_count, minio_prefix,
    split_train, split_val, split_test, is_frozen, created_by
) VALUES
(
    'DV-MATERIAL-LEVEL-V1-0', 'DS-MATERIAL-LEVEL-001', 'v1.0',
    1380, 'tianjing-datasets/sinter/SCENE-SINTER-FIRE-001/material-level/v1.0/',
    0.8000, 0.1500, 0.0500, TRUE, 'SYSTEM'
),
(
    'DV-MATERIAL-LEVEL-V1-1', 'DS-MATERIAL-LEVEL-001', 'v1.1',
    1380, 'tianjing-datasets/sinter/SCENE-SINTER-FIRE-001/material-level/v1.1/',
    0.8000, 0.1500, 0.0500, FALSE, 'SYSTEM'
);


-- ============================================================
-- PART 3：训练作业
-- 实验室阶段使用 CPU/单 GPU 训练（gpu_count=1）
-- 配置：epochs=200, batch_size=16, lr=0.01, img_size=640
-- ============================================================

-- ---------- 铸坯裂纹 - 初始训练（对应生产版 MV-BILLET-CRACK-20260320-001）----------
INSERT INTO train_job (
    job_id, plugin_id, dataset_version_id,
    trigger_type, train_config_json, gpu_count,
    status, best_epoch, best_map50, best_map50_95,
    mlflow_run_id,
    started_at, finished_at, created_by
) VALUES (
    'JOB-BILLET-CRACK-001',
    'HEAD-BILLET-CRACK-V1',
    'DV-BILLET-CRACK-V1-0',
    'MANUAL',
    '{"epochs":200,"batch_size":16,"lr":0.01,"img_size":640,"augment":{"flip_lr":0.5,"mosaic":0.8,"hsv_h":0.015,"hsv_s":0.7,"hsv_v":0.4}}',
    1,
    'COMPLETED', 163, 0.8630, 0.7180,
    'mlflow-run-billet-crack-001-a3f7d2e1',
    '2026-03-19 08:00:00+08', '2026-03-19 21:35:00+08',
    'SYSTEM'
);

-- ---------- 铸坯裂纹 - 增量训练（对应候选版 MV-BILLET-CRACK-20260401-002）----------
INSERT INTO train_job (
    job_id, plugin_id, dataset_version_id,
    trigger_type, train_config_json, gpu_count,
    status, best_epoch, best_map50, best_map50_95,
    mlflow_run_id,
    started_at, finished_at, created_by
) VALUES (
    'JOB-BILLET-CRACK-002',
    'HEAD-BILLET-CRACK-V1',
    'DV-BILLET-CRACK-V1-1',
    'MANUAL',
    '{"epochs":200,"batch_size":16,"lr":0.005,"img_size":640,"augment":{"flip_lr":0.5,"mosaic":0.8,"hsv_h":0.015,"hsv_s":0.7,"hsv_v":0.4},"fine_tune_from":"mlflow-run-billet-crack-001-a3f7d2e1"}',
    1,
    'COMPLETED', 178, 0.8810, 0.7360,
    'mlflow-run-billet-crack-002-b8c3a9f5',
    '2026-03-31 09:00:00+08', '2026-03-31 22:50:00+08',
    'SYSTEM'
);

-- ---------- 链篦机侧板检测 - 初始训练（对应生产版 MV-SIDEPLATE-20260322-001）----------
INSERT INTO train_job (
    job_id, plugin_id, dataset_version_id,
    trigger_type, train_config_json, gpu_count,
    status, best_epoch, best_map50, best_map50_95,
    mlflow_run_id,
    started_at, finished_at, created_by
) VALUES (
    'JOB-SIDEPLATE-001',
    'HEAD-SIDEPLATE-V1',
    'DV-SIDEPLATE-V1-0',
    'MANUAL',
    '{"epochs":200,"batch_size":16,"lr":0.01,"img_size":640,"augment":{"flip_lr":0.5,"mosaic":0.7,"translate":0.1,"scale":0.5}}',
    1,
    'COMPLETED', 158, 0.8870, 0.7450,
    'mlflow-run-sideplate-001-c4e5b1d8',
    '2026-03-21 08:30:00+08', '2026-03-21 19:15:00+08',
    'SYSTEM'
);

-- ---------- 烧结机壁条检测 - 初始训练（对应生产版 MV-WALLBAR-20260322-001）----------
INSERT INTO train_job (
    job_id, plugin_id, dataset_version_id,
    trigger_type, train_config_json, gpu_count,
    status, best_epoch, best_map50, best_map50_95,
    mlflow_run_id,
    started_at, finished_at, created_by
) VALUES (
    'JOB-WALLBAR-001',
    'ATOM-DETECT-YOLO-V1',
    'DV-WALLBAR-V1-0',
    'MANUAL',
    '{"epochs":200,"batch_size":16,"lr":0.01,"img_size":640,"augment":{"flip_lr":0.0,"mosaic":0.5,"hsv_h":0.02,"hsv_s":0.8,"hsv_v":0.4},"note":"烧结炉热成像场景增强去雾后训练"}',
    1,
    'COMPLETED', 171, 0.9100, 0.7620,
    'mlflow-run-wallbar-001-d2f6c3a7',
    '2026-03-21 14:00:00+08', '2026-03-22 03:20:00+08',
    'SYSTEM'
);

-- ---------- 钢材表面缺陷 - 初始训练（对应生产版 MV-STEEL-SURFACE-20260323-001）----------
INSERT INTO train_job (
    job_id, plugin_id, dataset_version_id,
    trigger_type, train_config_json, gpu_count,
    status, best_epoch, best_map50, best_map50_95,
    mlflow_run_id,
    started_at, finished_at, created_by
) VALUES (
    'JOB-STEEL-SURFACE-001',
    'HEAD-STEEL-SURFACE-V1',
    'DV-STEEL-SURFACE-V1-0',
    'MANUAL',
    '{"epochs":200,"batch_size":16,"lr":0.01,"img_size":640,"augment":{"flip_lr":0.5,"flip_ud":0.1,"mosaic":0.8,"copy_paste":0.1},"note":"多类缺陷联合训练：划伤/折叠/结疤/裂纹/凹坑"}',
    1,
    'COMPLETED', 182, 0.8980, 0.7560,
    'mlflow-run-steel-surface-001-e9a4d7b2',
    '2026-03-22 09:00:00+08', '2026-03-23 01:40:00+08',
    'SYSTEM'
);

-- ---------- 烧结看火料面 - 初始训练（对应生产版 MV-MATERIAL-LEVEL-20260325-001，Sprint 3 场景）----------
INSERT INTO train_job (
    job_id, plugin_id, dataset_version_id,
    trigger_type, train_config_json, gpu_count,
    status, best_epoch, best_map50, best_map50_95,
    mlflow_run_id,
    started_at, finished_at, created_by
) VALUES (
    'JOB-MATERIAL-LEVEL-001',
    'HEAD-MATERIAL-LEVEL-V1',
    'DV-MATERIAL-LEVEL-V1-0',
    'MANUAL',
    '{"epochs":200,"batch_size":16,"lr":0.01,"img_size":640,"augment":{"hsv_h":0.015,"hsv_s":0.7,"hsv_v":0.4,"flip_lr":0.5,"mosaic":0.7},"note":"高温烟雾环境，启用去雾预处理"}',
    1,
    'COMPLETED', 155, NULL, NULL,
    'mlflow-run-material-level-001-f3b8e2c6',
    '2026-03-24 10:00:00+08', '2026-03-24 19:30:00+08',
    'SYSTEM'
);

-- ---------- 漂移自动触发重训练（示例：模拟 AUTO_DRIFT 触发类型，状态 PENDING）----------
-- 注：此作业尚未开始，用于测试 TC-TRAIN-005（漂移自动触发重训练作业）
INSERT INTO train_job (
    job_id, plugin_id, dataset_version_id,
    trigger_type, train_config_json, gpu_count,
    status, created_by
) VALUES (
    'JOB-SIDEPLATE-DRIFT-001',
    'HEAD-SIDEPLATE-V1',
    'DV-SIDEPLATE-V1-1',
    'AUTO_DRIFT',
    '{"epochs":200,"batch_size":16,"lr":0.008,"img_size":640,"augment":{"flip_lr":0.5,"mosaic":0.8},"drift_trigger":{"consecutive_days":3,"precision_threshold":0.85}}',
    1,
    'PENDING',
    'SYSTEM'
);

-- ---------- 失败作业样例（用于测试失败重试、错误日志查询等用例）----------
INSERT INTO train_job (
    job_id, plugin_id, dataset_version_id,
    trigger_type, train_config_json, gpu_count,
    status, error_msg,
    started_at, finished_at, created_by
) VALUES (
    'JOB-WALLBAR-FAIL-001',
    'ATOM-DETECT-YOLO-V1',
    'DV-WALLBAR-V1-1',
    'MANUAL',
    '{"epochs":200,"batch_size":32,"lr":0.01,"img_size":640}',
    1,
    'FAILED',
    'CUDA out of memory: tried to allocate 2.4 GiB on device 0 with 3.8 GiB total capacity; batch_size=32 exceeds GPU memory limit for this model configuration.',
    '2026-04-01 15:00:00+08', '2026-04-01 15:08:00+08',
    'SYSTEM'
);


-- ============================================================
-- PART 4：模型产物（训练成功的作业对应产物）
-- 注：artifact_id 与 tianjing_prod.model_version.version_id 形成概念映射，
--     但两库无外键约束（跨库隔离）。MLflow run_id 是共同纽带。
-- ============================================================

-- 铸坯裂纹 - 初始版本产物（对应 MV-BILLET-CRACK-20260320-001）
INSERT INTO model_artifact (
    artifact_id, job_id, mlflow_run_id,
    model_path, export_format,
    map50, map50_95, precision_score, recall_score,
    inference_ms_gpu, inference_ms_cpu, model_size_mb,
    created_by
) VALUES (
    'ARFCT-BILLET-CRACK-001',
    'JOB-BILLET-CRACK-001',
    'mlflow-run-billet-crack-001-a3f7d2e1',
    'tianjing-models-staging/steel/billet-crack/v1.0.0/model.onnx',
    'ONNX',
    0.8630, 0.7180, 0.8910, 0.8420,
    38.0, 510.0, 85.6,
    'SYSTEM'
);

-- 铸坯裂纹 - 候选版本产物（对应 MV-BILLET-CRACK-20260401-002，当前 Sandbox 验证中）
INSERT INTO model_artifact (
    artifact_id, job_id, mlflow_run_id,
    model_path, export_format,
    map50, map50_95, precision_score, recall_score,
    inference_ms_gpu, inference_ms_cpu, model_size_mb,
    created_by
) VALUES (
    'ARFCT-BILLET-CRACK-002',
    'JOB-BILLET-CRACK-002',
    'mlflow-run-billet-crack-002-b8c3a9f5',
    'tianjing-models-staging/steel/billet-crack/v1.1.0/model.onnx',
    'ONNX',
    0.8810, 0.7360, 0.9060, 0.8590,
    36.5, 495.0, 83.2,
    'SYSTEM'
);

-- 链篦机侧板检测 - 初始版本产物（对应 MV-SIDEPLATE-20260322-001）
INSERT INTO model_artifact (
    artifact_id, job_id, mlflow_run_id,
    model_path, export_format,
    map50, map50_95, precision_score, recall_score,
    inference_ms_gpu, inference_ms_cpu, model_size_mb,
    created_by
) VALUES (
    'ARFCT-SIDEPLATE-001',
    'JOB-SIDEPLATE-001',
    'mlflow-run-sideplate-001-c4e5b1d8',
    'tianjing-models-staging/pellet/sideplate/v1.0.0/model.onnx',
    'ONNX',
    0.8870, 0.7450, 0.9020, 0.8750,
    20.0, 230.0, 62.3,
    'SYSTEM'
);

-- 烧结机壁条检测 - 初始版本产物（对应 MV-WALLBAR-20260322-001）
INSERT INTO model_artifact (
    artifact_id, job_id, mlflow_run_id,
    model_path, export_format,
    map50, map50_95, precision_score, recall_score,
    inference_ms_gpu, inference_ms_cpu, model_size_mb,
    created_by
) VALUES (
    'ARFCT-WALLBAR-001',
    'JOB-WALLBAR-001',
    'mlflow-run-wallbar-001-d2f6c3a7',
    'tianjing-models-staging/sinter/wallbar/v1.0.0/model.onnx',
    'ONNX',
    0.9100, 0.7620, 0.9230, 0.8880,
    18.0, 215.0, 58.0,
    'SYSTEM'
);

-- 钢材表面缺陷 - 初始版本产物（对应 MV-STEEL-SURFACE-20260323-001）
INSERT INTO model_artifact (
    artifact_id, job_id, mlflow_run_id,
    model_path, export_format,
    map50, map50_95, precision_score, recall_score,
    inference_ms_gpu, inference_ms_cpu, model_size_mb,
    created_by
) VALUES (
    'ARFCT-STEEL-SURFACE-001',
    'JOB-STEEL-SURFACE-001',
    'mlflow-run-steel-surface-001-e9a4d7b2',
    'tianjing-models-staging/section/steel-surface/v1.0.0/model.onnx',
    'ONNX',
    0.8980, 0.7560, 0.9150, 0.8810,
    19.0, 225.0, 65.2,
    'SYSTEM'
);

-- 烧结看火料面 - 初始版本产物（对应 MV-MATERIAL-LEVEL-20260325-001）
INSERT INTO model_artifact (
    artifact_id, job_id, mlflow_run_id,
    model_path, export_format,
    map50, map50_95, precision_score, recall_score,
    inference_ms_gpu, inference_ms_cpu, model_size_mb,
    created_by
) VALUES (
    'ARFCT-MATERIAL-LEVEL-001',
    'JOB-MATERIAL-LEVEL-001',
    'mlflow-run-material-level-001-f3b8e2c6',
    'tianjing-models-staging/sinter/material-level/v1.0.0/model.onnx',
    'ONNX',
    NULL, NULL, 0.9280, 0.9060,
    9.0, 92.0, 42.1,
    'SYSTEM'
);


-- ============================================================
-- PART 5：标注任务（annotation_task）
-- 对应各数据集版本的人工标注工作，用于测试标注管理页面和进度查询
-- ============================================================

INSERT INTO annotation_task (
    task_id, dataset_version_id, label_studio_project_id,
    task_name, annotation_type,
    total_samples, annotated_samples, reviewed_samples,
    status, assigned_to, review_by,
    iou_threshold, started_at, finished_at, created_by
) VALUES

-- 铸坯裂纹 v1.0 标注任务（已完成）
(
    'ANNO-BILLET-CRACK-V1-0',
    'DV-BILLET-CRACK-V1-0',
    101,
    '铸坯裂纹检测数据集 v1.0 标注任务',
    'SEGMENTATION',
    2000, 2000, 2000,
    'COMPLETED', 'test_sandbox_op', 'test_reviewer_a',
    0.750,
    '2026-03-10 09:00:00+08', '2026-03-17 18:00:00+08',
    'SYSTEM'
),

-- 铸坯裂纹 v1.1 标注任务（已完成，新增样本）
(
    'ANNO-BILLET-CRACK-V1-1',
    'DV-BILLET-CRACK-V1-1',
    102,
    '铸坯裂纹检测数据集 v1.1 增量标注任务',
    'SEGMENTATION',
    350, 350, 350,
    'COMPLETED', 'test_sandbox_op', 'test_reviewer_a',
    0.750,
    '2026-03-25 09:00:00+08', '2026-03-30 17:30:00+08',
    'SYSTEM'
),

-- 链篦机侧板 v1.0 标注任务（已完成）
(
    'ANNO-SIDEPLATE-V1-0',
    'DV-SIDEPLATE-V1-0',
    103,
    '链篦机侧板跑偏数据集 v1.0 标注任务',
    'DETECTION',
    1920, 1920, 1920,
    'COMPLETED', 'test_sandbox_op', 'test_reviewer_b',
    0.750,
    '2026-03-12 09:00:00+08', '2026-03-19 18:00:00+08',
    'SYSTEM'
),

-- 烧结看火料面 v1.0 标注任务（已完成）
(
    'ANNO-MATERIAL-LEVEL-V1-0',
    'DV-MATERIAL-LEVEL-V1-0',
    104,
    '烧结看火料面状态数据集 v1.0 标注任务',
    'CLASSIFICATION',
    1380, 1380, 1380,
    'COMPLETED', 'test_sandbox_op', 'test_reviewer_a',
    0.750,
    '2026-03-14 09:00:00+08', '2026-03-21 17:00:00+08',
    'SYSTEM'
),

-- 烧结机壁条 v1.1 标注任务（进行中，用于测试 IN_PROGRESS 状态）
(
    'ANNO-WALLBAR-V1-1',
    'DV-WALLBAR-V1-1',
    105,
    '烧结机壁条检测数据集 v1.1 增量标注任务',
    'DETECTION',
    500, 320, 0,
    'IN_PROGRESS', 'test_sandbox_op', NULL,
    0.750,
    '2026-04-01 09:00:00+08', NULL,
    'SYSTEM'
);
