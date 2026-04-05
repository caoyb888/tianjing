-- V6: 仿真任务新增进度字段，Phase 2 仿真执行引擎使用
ALTER TABLE simulation_task
    ADD COLUMN IF NOT EXISTS progress INT NOT NULL DEFAULT 0;

COMMENT ON COLUMN simulation_task.progress IS '仿真执行进度（0-100），每 10 帧更新一次';
