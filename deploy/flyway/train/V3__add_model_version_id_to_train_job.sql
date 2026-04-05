-- V3: 训练作业关联自动注册的模型版本
-- 背景：T-06 训练完成后自动注册 model_version，job 需记录对应的 version_id
--       供前端"一键跳转到模型审核"功能使用

ALTER TABLE train_job
    ADD COLUMN model_version_id VARCHAR(32) NULL;

COMMENT ON COLUMN train_job.model_version_id IS
    '训练完成后自动注册的模型版本 ID（如 MV-XXXXXXXX），由训练脚本回调写入';

-- V2 种子数据在本列存在前写入，此处补充回填，与 tianjing_prod.model_version 保持对应
UPDATE train_job SET model_version_id = 'MV-BILLET-CRACK-20260320-001'   WHERE job_id = 'JOB-BILLET-CRACK-001';
UPDATE train_job SET model_version_id = 'MV-BILLET-CRACK-20260401-002'   WHERE job_id = 'JOB-BILLET-CRACK-002';
UPDATE train_job SET model_version_id = 'MV-SIDEPLATE-20260322-001'       WHERE job_id = 'JOB-SIDEPLATE-001';
UPDATE train_job SET model_version_id = 'MV-WALLBAR-20260322-001'         WHERE job_id = 'JOB-WALLBAR-001';
UPDATE train_job SET model_version_id = 'MV-STEEL-SURFACE-20260323-001'   WHERE job_id = 'JOB-STEEL-SURFACE-001';
UPDATE train_job SET model_version_id = 'MV-MATERIAL-LEVEL-20260325-001'  WHERE job_id = 'JOB-MATERIAL-LEVEL-001';
