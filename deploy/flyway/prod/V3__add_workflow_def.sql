-- V3: 低代码工作流定义表
-- Sprint S1-21: lowcode-workflow-service
-- 规范：CLAUDE.md §7.1（PostgreSQL 规范）

CREATE TABLE workflow_def (
    id              BIGSERIAL       PRIMARY KEY,
    workflow_id     VARCHAR(128)    NOT NULL UNIQUE,
    scene_id        VARCHAR(64)     NOT NULL,
    workflow_name   VARCHAR(128)    NOT NULL,
    status          VARCHAR(32)     NOT NULL DEFAULT 'DRAFT',  -- DRAFT | PUBLISHED | ARCHIVED
    workflow_json   JSONB           NOT NULL DEFAULT '{}',
    version         INTEGER         NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(64)     NOT NULL,
    is_deleted      SMALLINT        NOT NULL DEFAULT 0
);

CREATE INDEX idx_workflow_def_scene_id ON workflow_def (scene_id) WHERE is_deleted = 0;
CREATE INDEX idx_workflow_def_status   ON workflow_def (status)   WHERE is_deleted = 0;

COMMENT ON TABLE  workflow_def               IS '低代码工作流定义表（S1-21，lowcode-workflow-service）';
COMMENT ON COLUMN workflow_def.workflow_id   IS '工作流唯一ID，格式：WF-{sceneId}-{随机}';
COMMENT ON COLUMN workflow_def.status        IS '工作流状态：DRAFT=草稿, PUBLISHED=已发布, ARCHIVED=已归档';
COMMENT ON COLUMN workflow_def.workflow_json IS '工作流画布 JSON，结构：{nodes:[{id,type,config,position}], edges:[{id,source,target}], metadata:{}}';
COMMENT ON COLUMN workflow_def.version       IS '乐观锁版本号，MyBatis-Plus @Version';
COMMENT ON COLUMN workflow_def.is_deleted    IS '软删除标记：0=正常, 1=已删除';

-- 更新时间自动触发器
CREATE TRIGGER trg_workflow_def_updated_at
    BEFORE UPDATE ON workflow_def
    FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at();
