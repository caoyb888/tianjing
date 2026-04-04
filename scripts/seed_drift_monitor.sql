-- ============================================================
-- 种子数据脚本：drift-monitor-service 本地开发环境
-- 适用：Sprint 3 端到端训练链路验证（S3-13）
-- 编制：2026-04-04
--
-- 使用方式（Server 1 本地开发环境）：
--   # 漂移指标写入生产库
--   psql $TIANJING_POSTGRES_PROD_URL   -f scripts/seed_drift_monitor.sql
--   # Sprint 3 测试作业写入训练库
--   psql $TIANJING_POSTGRES_TRAIN_URL  -f scripts/seed_drift_monitor.sql
--
-- 说明：
--   tianjing_prod  的漂移指标（SECTION A）和 tianjing_train 的训练数据（SECTION B）
--   写在同一文件中；psql 会忽略不属于当前库的表引用并报错，可分开执行两段。
--   或直接运行：scripts/start-backend.sh 启动服务后，通过 API 验证接口。
--
-- 依赖：
--   tianjing_prod  ：V1-V4 Flyway 迁移已执行（scene_config / model_version 已存在）
--   tianjing_train ：V1-V3 Flyway 迁移已执行（dataset_version / train_job 已存在）
-- ============================================================


-- ============================================================
-- SECTION A：tianjing_prod — drift_metric 漂移指标（7 天）
-- 在 tianjing_prod 数据库中执行
-- ============================================================

-- 覆盖两个场景：
--   SCENE-SINTER-FIRE-001（烧结看火）：最近 3 天精度低于 85%，触发重训练
--   SCENE-STEEL-001（连铸铸坯缺陷）：7 天精度稳定，作为对照参照

-- ────────────────────────────────────────────────────────────
-- 烧结看火场景：精度漂移场景（连续 3 天低于阈值，自动触发重训练）
-- 当前生产模型：MV-MATERIAL-LEVEL-20260325-001
-- ────────────────────────────────────────────────────────────
INSERT INTO drift_metric (
    metric_id, scene_id, model_version_id,
    metric_date,
    total_alarms, tp_count, fp_count, fn_count,
    precision_val, recall_val, f1_score,
    feedback_coverage,
    is_below_threshold, retrain_triggered, retrain_job_id,
    consecutive_below, created_by
) VALUES

-- D-7：精度正常（0.8920）
(
    'DM-SINTER-FIRE-20260329', 'SCENE-SINTER-FIRE-001', 'MV-MATERIAL-LEVEL-20260325-001',
    '2026-03-29',
    120, 107, 13, 8,
    0.8917, 0.9305, 0.9107,
    0.7833,
    FALSE, FALSE, NULL,
    0, 'SYSTEM'
),

-- D-6：精度正常（0.8840）
(
    'DM-SINTER-FIRE-20260330', 'SCENE-SINTER-FIRE-001', 'MV-MATERIAL-LEVEL-20260325-001',
    '2026-03-30',
    115, 101, 14, 10,
    0.8783, 0.9099, 0.8938,
    0.7652,
    FALSE, FALSE, NULL,
    0, 'SYSTEM'
),

-- D-5：精度轻微下降，仍在阈值以上（0.8630）
(
    'DM-SINTER-FIRE-20260331', 'SCENE-SINTER-FIRE-001', 'MV-MATERIAL-LEVEL-20260325-001',
    '2026-03-31',
    118, 101, 17, 12,
    0.8559, 0.8940, 0.8745,
    0.7542,
    FALSE, FALSE, NULL,
    0, 'SYSTEM'
),

-- D-4：精度首次跌破 85%（0.8260），consecutive_below=1
(
    'DM-SINTER-FIRE-20260401', 'SCENE-SINTER-FIRE-001', 'MV-MATERIAL-LEVEL-20260325-001',
    '2026-04-01',
    122, 100, 22, 16,
    0.8197, 0.8621, 0.8404,
    0.7295,
    TRUE, FALSE, NULL,
    1, 'SYSTEM'
),

-- D-3：精度继续下滑（0.7940），consecutive_below=2
(
    'DM-SINTER-FIRE-20260402', 'SCENE-SINTER-FIRE-001', 'MV-MATERIAL-LEVEL-20260325-001',
    '2026-04-02',
    125, 98, 27, 20,
    0.7840, 0.8305, 0.8066,
    0.7040,
    TRUE, FALSE, NULL,
    2, 'SYSTEM'
),

-- D-2：第 3 天低于阈值（0.7620），consecutive_below=3，自动触发重训练
--       retrain_job_id 指向 tianjing_train.train_job.job_id（跨库引用，无 FK 约束）
(
    'DM-SINTER-FIRE-20260403', 'SCENE-SINTER-FIRE-001', 'MV-MATERIAL-LEVEL-20260325-001',
    '2026-04-03',
    130, 98, 32, 24,
    0.7538, 0.8033, 0.7778,
    0.6923,
    TRUE, TRUE, 'JOB-SINTER-FIRE-DRIFT-001',
    3, 'SYSTEM'
),

-- D-1（今日）：重训练进行中，生产模型精度仍在监测（0.7750）
(
    'DM-SINTER-FIRE-20260404', 'SCENE-SINTER-FIRE-001', 'MV-MATERIAL-LEVEL-20260325-001',
    '2026-04-04',
    128, 99, 29, 22,
    0.7732, 0.8182, 0.7951,
    0.6875,
    TRUE, FALSE, NULL,
    4, 'SYSTEM'
)

ON CONFLICT (scene_id, metric_date) DO NOTHING;


-- ────────────────────────────────────────────────────────────
-- 铸坯缺陷场景：精度稳定（7 天均高于 85%，作为对照）
-- 当前生产模型：MV-BILLET-CRACK-20260320-001
-- ────────────────────────────────────────────────────────────
INSERT INTO drift_metric (
    metric_id, scene_id, model_version_id,
    metric_date,
    total_alarms, tp_count, fp_count, fn_count,
    precision_val, recall_val, f1_score,
    feedback_coverage,
    is_below_threshold, retrain_triggered, retrain_job_id,
    consecutive_below, created_by
) VALUES

('DM-STEEL-001-20260329', 'SCENE-STEEL-001', 'MV-BILLET-CRACK-20260320-001',
 '2026-03-29', 95, 85, 10, 7,  0.8947, 0.9239, 0.9091, 0.8421, FALSE, FALSE, NULL, 0, 'SYSTEM'),

('DM-STEEL-001-20260330', 'SCENE-STEEL-001', 'MV-BILLET-CRACK-20260320-001',
 '2026-03-30', 98, 88, 10, 8,  0.8980, 0.9167, 0.9072, 0.8571, FALSE, FALSE, NULL, 0, 'SYSTEM'),

('DM-STEEL-001-20260331', 'SCENE-STEEL-001', 'MV-BILLET-CRACK-20260320-001',
 '2026-03-31', 102, 91, 11, 9, 0.8921, 0.9100, 0.9010, 0.8431, FALSE, FALSE, NULL, 0, 'SYSTEM'),

('DM-STEEL-001-20260401', 'SCENE-STEEL-001', 'MV-BILLET-CRACK-20260320-001',
 '2026-04-01', 100, 89, 11, 8, 0.8900, 0.9175, 0.9035, 0.8500, FALSE, FALSE, NULL, 0, 'SYSTEM'),

('DM-STEEL-001-20260402', 'SCENE-STEEL-001', 'MV-BILLET-CRACK-20260320-001',
 '2026-04-02', 97,  87, 10, 7, 0.8969, 0.9255, 0.9110, 0.8557, FALSE, FALSE, NULL, 0, 'SYSTEM'),

('DM-STEEL-001-20260403', 'SCENE-STEEL-001', 'MV-BILLET-CRACK-20260320-001',
 '2026-04-03', 99,  89, 10, 8, 0.8990, 0.9175, 0.9082, 0.8485, FALSE, FALSE, NULL, 0, 'SYSTEM'),

('DM-STEEL-001-20260404', 'SCENE-STEEL-001', 'MV-BILLET-CRACK-20260320-001',
 '2026-04-04', 101, 91, 10, 9, 0.9010, 0.9097, 0.9053, 0.8614, FALSE, FALSE, NULL, 0, 'SYSTEM')

ON CONFLICT (scene_id, metric_date) DO NOTHING;


-- ============================================================
-- SECTION B：tianjing_train — Sprint 3 测试数据
-- 在 tianjing_train 数据库中执行
-- ============================================================

-- ────────────────────────────────────────────────────────────
-- Sprint 3 漂移触发重训练作业（对应 SECTION A 中 retrain_job_id）
-- plugin_id 使用 ATOM-DETECT-YOLO-V1（docker run 训练容器已支持此 ID）
-- dataset_version_id 使用 DV-MATERIAL-LEVEL-V1-1（V2 迁移已存在，is_frozen=FALSE）
-- ────────────────────────────────────────────────────────────
INSERT INTO train_job (
    job_id, plugin_id, dataset_version_id,
    trigger_type, drift_metric_id,
    train_config_json, gpu_count,
    status, created_by
) VALUES (
    'JOB-SINTER-FIRE-DRIFT-001',
    'ATOM-DETECT-YOLO-V1',
    'DV-MATERIAL-LEVEL-V1-1',
    'AUTO_DRIFT',
    'DM-SINTER-FIRE-20260403',
    '{"epochs":10,"img_size":640,"batch_size":8,"note":"Sprint 3 CPU 模式验证，精度门槛宽松"}',
    1,
    'PENDING',
    'SYSTEM'
) ON CONFLICT (job_id) DO NOTHING;


-- ────────────────────────────────────────────────────────────
-- Sprint 3 手动测试作业（供开发人员在本地直接执行 docker run 验证训练链路）
-- 状态 PENDING：提交此 job_id 后可直接调用 DockerTrainJobLauncher 启动容器
-- ────────────────────────────────────────────────────────────
INSERT INTO train_job (
    job_id, plugin_id, dataset_version_id,
    trigger_type,
    train_config_json, gpu_count,
    status, created_by
) VALUES (
    'JOB-SINTER-FIRE-SPRINT3-001',
    'ATOM-DETECT-YOLO-V1',
    'DV-MATERIAL-LEVEL-V1-1',
    'MANUAL',
    '{"epochs":10,"img_size":640,"batch_size":8,"note":"Sprint 3 S3-13 全链路验证专用，CPU 模式"}',
    1,
    'PENDING',
    'test_admin'
) ON CONFLICT (job_id) DO NOTHING;


-- ────────────────────────────────────────────────────────────
-- 烧结看火场景已完成的历史作业（含 model_version_id，展示 T-06 跳转效果）
-- 前提：V3 迁移（deploy/flyway/train/V3__add_model_version_id_to_train_job.sql）已执行
-- ────────────────────────────────────────────────────────────
INSERT INTO train_job (
    job_id, plugin_id, dataset_version_id,
    trigger_type,
    train_config_json, gpu_count,
    status, best_epoch, best_map50, best_map50_95,
    mlflow_run_id, model_version_id,
    started_at, finished_at, created_by
) VALUES (
    'JOB-SINTER-FIRE-CPU-001',
    'ATOM-DETECT-YOLO-V1',
    'DV-MATERIAL-LEVEL-V1-0',
    'MANUAL',
    '{"epochs":10,"img_size":640,"batch_size":8,"note":"Sprint 3 CPU 模式首次验证"}',
    1,
    'COMPLETED', 7, 0.2840, 0.1520,
    'mlflow-run-sinter-fire-cpu-20260403',
    'MV-SINTER-FIRE-CPU-20260403-001',
    '2026-04-03 14:00:00+08', '2026-04-03 16:35:00+08',
    'test_admin'
) ON CONFLICT (job_id) DO NOTHING;


-- ────────────────────────────────────────────────────────────
-- 验证查询（可在 psql 中手动执行，确认数据写入正确）
-- ────────────────────────────────────────────────────────────

-- tianjing_prod 执行：查看漂移场景汇总
-- SELECT scene_id, metric_date, precision_val, consecutive_below, retrain_triggered
-- FROM drift_metric
-- WHERE scene_id IN ('SCENE-SINTER-FIRE-001', 'SCENE-STEEL-001')
-- ORDER BY scene_id, metric_date;

-- tianjing_train 执行：查看 Sprint 3 测试作业
-- SELECT job_id, plugin_id, status, best_map50, model_version_id, created_at
-- FROM train_job
-- WHERE plugin_id = 'ATOM-DETECT-YOLO-V1'
-- ORDER BY created_at DESC;
