-- V5: 仿真任务一键生成训练集 — Phase 1 导出引擎
-- 背景：仿真任务完成后支持一键导出 COCO 数据集到 tianjing-datasets bucket

ALTER TABLE simulation_task
    ADD COLUMN dataset_version_id VARCHAR(64)  NULL,
    ADD COLUMN export_status      VARCHAR(16)  NULL,
    ADD COLUMN export_result_json TEXT         NULL;

COMMENT ON COLUMN simulation_task.dataset_version_id IS
    '导出的数据集版本 ID，导出成功后写入，用于溯源（如 DV-MATERIAL-LEVEL-V1-2）';
COMMENT ON COLUMN simulation_task.export_status IS
    '数据集导出状态：NULL / EXPORTING / EXPORTED / EXPORT_FAILED';
COMMENT ON COLUMN simulation_task.export_result_json IS
    '导出结果摘要 JSON：{total_frames, exported_frames, annotation_count, minio_path, error_msg}';
