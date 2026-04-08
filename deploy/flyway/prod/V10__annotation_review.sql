-- ============================================================================
-- V10__annotation_review.sql
-- 标注审核工具数据库迁移脚本
-- 适用范围：tianjing_prod
-- 关联文档：标注审核工具开发计划 V1.0
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 创建 simulation_frame_review 表（帧级标注审核记录）
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS simulation_frame_review (
    id                          BIGSERIAL      PRIMARY KEY,
    task_id                     VARCHAR(64)    NOT NULL,   -- 关联 simulation_task.task_id
    frame_id                    VARCHAR(64)    NOT NULL,   -- 帧编号，如 frame_000001
    frame_url                   VARCHAR(512)   NOT NULL,   -- MinIO 帧图像地址
    frame_index                 INTEGER        NOT NULL,   -- 帧序号（0-based，用于分页排序）

    -- 推理原始结果（初始化时从 result_json 复制，只读参考）
    original_detections_json    TEXT           NOT NULL DEFAULT '[]',

    -- 人工校正结果（NULL = 未修改使用原始结果；'[]' = 明确标注为无目标负样本）
    corrected_detections_json   TEXT           NULL,

    review_status   VARCHAR(16)    NOT NULL DEFAULT 'PENDING',
    -- PENDING   = 未审核
    -- APPROVED  = 通过（含 is_modified=false 原样通过 / is_modified=true 修改后通过）
    -- REJECTED  = 排除（此帧不纳入训练集）
    -- SKIPPED   = 暂跳过（等同 PENDING，导出时按配置决定是否包含）

    is_modified     BOOLEAN        NOT NULL DEFAULT false, -- 人工是否修改过检测框
    reviewed_by     VARCHAR(64)    NULL,
    reviewed_at     TIMESTAMPTZ    NULL,
    
    -- 乐观锁版本号
    version         INTEGER        NOT NULL DEFAULT 0,
    
    -- 软删除标记
    is_deleted      BOOLEAN        NOT NULL DEFAULT false,
    
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_frame_review UNIQUE (task_id, frame_id),
    CONSTRAINT ck_review_status CHECK (review_status IN ('PENDING','APPROVED','REJECTED','SKIPPED'))
);

-- 注释
COMMENT ON TABLE simulation_frame_review IS '仿真任务帧级标注审核记录';
COMMENT ON COLUMN simulation_frame_review.task_id IS '关联仿真任务ID';
COMMENT ON COLUMN simulation_frame_review.frame_id IS '帧编号';
COMMENT ON COLUMN simulation_frame_review.frame_url IS 'MinIO帧图像地址';
COMMENT ON COLUMN simulation_frame_review.frame_index IS '帧序号（0-based）';
COMMENT ON COLUMN simulation_frame_review.original_detections_json IS '原始推理检测框JSON';
COMMENT ON COLUMN simulation_frame_review.corrected_detections_json IS '人工校正后检测框JSON（NULL表示未修改）';
COMMENT ON COLUMN simulation_frame_review.review_status IS '审核状态：PENDING/APPROVED/REJECTED/SKIPPED';
COMMENT ON COLUMN simulation_frame_review.is_modified IS '是否人工修改过';
COMMENT ON COLUMN simulation_frame_review.reviewed_by IS '审核人';
COMMENT ON COLUMN simulation_frame_review.reviewed_at IS '审核时间';
COMMENT ON COLUMN simulation_frame_review.version IS '乐观锁版本号';
COMMENT ON COLUMN simulation_frame_review.is_deleted IS '软删除标记';

-- ----------------------------------------------------------------------------
-- 2. 创建索引
-- ----------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_frame_review_task_id 
    ON simulation_frame_review (task_id);

CREATE INDEX IF NOT EXISTS idx_frame_review_task_status 
    ON simulation_frame_review (task_id, review_status);

CREATE INDEX IF NOT EXISTS idx_frame_review_task_index 
    ON simulation_frame_review (task_id, frame_index);

-- ----------------------------------------------------------------------------
-- 3. 为 simulation_task 表新增审核进度字段（可选，可通过实时COUNT查询代替）
-- ----------------------------------------------------------------------------
ALTER TABLE simulation_task 
    ADD COLUMN IF NOT EXISTS review_progress INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN simulation_task.review_progress IS '审核进度：0=未开始，1~99=审核中，100=全部完成';

-- ----------------------------------------------------------------------------
-- 4. 授权（历史回放服务使用）
-- ----------------------------------------------------------------------------
-- 注意：实际授权由 DBA 执行，此处仅作记录
-- GRANT SELECT, INSERT, UPDATE, DELETE ON simulation_frame_review TO tianjing_replay_user;
