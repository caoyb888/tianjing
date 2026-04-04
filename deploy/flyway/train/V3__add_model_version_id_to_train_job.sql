-- V3: 训练作业关联自动注册的模型版本
-- 背景：T-06 训练完成后自动注册 model_version，job 需记录对应的 version_id
--       供前端"一键跳转到模型审核"功能使用

ALTER TABLE train_job
    ADD COLUMN model_version_id VARCHAR(32) NULL;

COMMENT ON COLUMN train_job.model_version_id IS
    '训练完成后自动注册的模型版本 ID（如 MV-XXXXXXXX），由训练脚本回调写入';
