-- V9: 仿真任务多视频支持
-- 新增 simulation_video 表，实现一个仿真任务关联多个视频文件
-- video_file_url 字段保留，向后兼容（指向第一个视频）

CREATE TABLE simulation_video (
    id           BIGSERIAL      PRIMARY KEY,
    video_id     VARCHAR(64)    NOT NULL,
    task_id      VARCHAR(64)    NOT NULL,               -- 关联 simulation_task.task_id
    video_url    VARCHAR(512)   NOT NULL,               -- MinIO 视频地址
    video_name   VARCHAR(256)   NULL,                   -- 原始文件名
    label        VARCHAR(16)    NOT NULL DEFAULT 'MIXED',  -- NORMAL / ABNORMAL / MIXED
    sort_order   INTEGER        NOT NULL DEFAULT 0,     -- 上传顺序（影响执行顺序）
    duration_s   INTEGER        NULL,                   -- 视频时长（秒，推断后填入）
    status       VARCHAR(16)    NOT NULL DEFAULT 'PENDING', -- PENDING / RUNNING / COMPLETED / FAILED
    total_frames INTEGER        NOT NULL DEFAULT 0,
    matched_alarms INTEGER      NOT NULL DEFAULT 0,
    error_msg    TEXT           NULL,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by   VARCHAR(64)    NOT NULL,

    CONSTRAINT uq_simulation_video_id UNIQUE (video_id),
    CONSTRAINT ck_sim_video_label  CHECK (label  IN ('NORMAL','ABNORMAL','MIXED')),
    CONSTRAINT ck_sim_video_status CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED'))
);

CREATE INDEX idx_simvideo_task_id ON simulation_video (task_id);
CREATE INDEX idx_simvideo_task_order ON simulation_video (task_id, sort_order);

COMMENT ON TABLE simulation_video IS '仿真任务视频文件表（一任务对多视频）';
COMMENT ON COLUMN simulation_video.label IS 'NORMAL=正常录像 ABNORMAL=异常/含缺陷录像 MIXED=混合';

-- 把已有任务的 video_file_url 迁移到 simulation_video 表
INSERT INTO simulation_video (video_id, task_id, video_url, video_name, label, sort_order, created_by)
SELECT
    'SV-' || UPPER(SUBSTRING(REPLACE(gen_random_uuid()::text, '-', ''), 1, 12)),
    task_id,
    video_file_url,
    task_name,
    'MIXED',
    0,
    created_by
FROM simulation_task
WHERE video_file_url IS NOT NULL
  AND is_deleted = false;
