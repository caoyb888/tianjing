-- ============================================================
-- Flyway 迁移脚本 V1
-- 数据库：tianjing_sandbox（Sandbox 实验室库）
-- Sprint：S0-03
-- 说明：创建 Sandbox 库全部 4 张表
--       此库仅 sandbox-infer-service 可读写
--       禁止生产服务访问；禁止与生产库建立外键或视图
-- ============================================================

-- ============================================================
-- 4.1 Sandbox 推理会话表 sandbox_infer_session
-- ============================================================
CREATE TABLE sandbox_infer_session (
    id                  BIGSERIAL    PRIMARY KEY,
    session_id          VARCHAR(64)  NOT NULL,
    scene_id            VARCHAR(64)  NOT NULL,
    candidate_model_id  VARCHAR(64)  NOT NULL,
    prod_model_id       VARCHAR(64)  NOT NULL,
    mirror_fps          SMALLINT     NOT NULL DEFAULT 5,
    status              VARCHAR(20)  NOT NULL DEFAULT 'RUNNING',
    start_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    end_at              TIMESTAMPTZ  NULL,
    total_frames        INTEGER      NOT NULL DEFAULT 0,
    total_anomaly_frames INTEGER     NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(64)  NOT NULL,

    CONSTRAINT uk_session_id UNIQUE (session_id),
    CONSTRAINT ck_session_status CHECK (status IN ('RUNNING','PAUSED','COMPLETED','PROMOTED','ABORTED'))
);

COMMENT ON TABLE  sandbox_infer_session              IS 'Sandbox 推理会话表，一次 Sandbox 实验对应一条记录';
COMMENT ON COLUMN sandbox_infer_session.mirror_fps   IS 'T型分流镜像帧率，默认5fps（生产为25fps），降低实验室资源消耗';
COMMENT ON COLUMN sandbox_infer_session.status       IS 'RUNNING=进行中，PAUSED=暂停，COMPLETED=完成未转正，PROMOTED=已转正生产，ABORTED=中止';

CREATE INDEX idx_sb_session_scene ON sandbox_infer_session (scene_id, start_at DESC);

-- ============================================================
-- 4.1.1 Sandbox 推理结果明细表 sandbox_infer_result
-- ============================================================
CREATE TABLE sandbox_infer_result (
    id             BIGSERIAL     PRIMARY KEY,
    session_id     VARCHAR(64)   NOT NULL,
    frame_id       VARCHAR(64)   NOT NULL,
    scene_id       VARCHAR(64)   NOT NULL,
    detections_json JSONB        NOT NULL DEFAULT '[]',
    inference_ms   DECIMAL(8,3)  NOT NULL,
    has_anomaly    BOOLEAN       NOT NULL DEFAULT FALSE,
    top_class_name VARCHAR(64)   NULL,
    confidence     DECIMAL(5,4)  NULL,
    infer_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_result_session FOREIGN KEY (session_id) REFERENCES sandbox_infer_session(session_id)
);

COMMENT ON TABLE  sandbox_infer_result                IS 'Sandbox 单帧推理结果明细表，关联 sandbox_infer_session';
COMMENT ON COLUMN sandbox_infer_result.detections_json IS '检测结果列表 JSON，结构：[{class_id, class_name, confidence, bbox:{x1,y1,x2,y2}}]';

CREATE INDEX idx_sb_result_session ON sandbox_infer_result (session_id, infer_at DESC);
CREATE INDEX idx_sb_result_frame   ON sandbox_infer_result (frame_id);

-- ============================================================
-- 4.2 Sandbox 对比分析报告表 sandbox_compare_report
-- ============================================================
CREATE TABLE sandbox_compare_report (
    id                    BIGSERIAL     PRIMARY KEY,
    report_id             VARCHAR(64)   NOT NULL,
    session_id            VARCHAR(64)   NOT NULL,
    scene_id              VARCHAR(64)   NOT NULL,
    hours_evaluated       INTEGER       NOT NULL,
    sandbox_precision     DECIMAL(6,4)  NULL,
    sandbox_recall        DECIMAL(6,4)  NULL,
    prod_precision        DECIMAL(6,4)  NULL,
    prod_recall           DECIMAL(6,4)  NULL,
    precision_delta       DECIMAL(7,4)  NULL,
    potential_gain_count  INTEGER       NOT NULL DEFAULT 0,
    sandbox_gpu_mb        INTEGER       NULL,
    prod_gpu_mb           INTEGER       NULL,
    sandbox_p99_ms        DECIMAL(8,3)  NULL,
    prod_p99_ms           DECIMAL(8,3)  NULL,
    gate_passed           BOOLEAN       NOT NULL DEFAULT FALSE,
    gate_detail_json      JSONB         NULL,
    promote_status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    applied_at            TIMESTAMPTZ   NULL,
    approved_by           VARCHAR(64)   NULL,
    approved_at           TIMESTAMPTZ   NULL,
    generated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_report_id      UNIQUE (report_id),
    CONSTRAINT fk_report_session FOREIGN KEY (session_id) REFERENCES sandbox_infer_session(session_id),
    CONSTRAINT ck_promote_status CHECK (promote_status IN ('PENDING','APPLIED','APPROVED','REJECTED','PROMOTED'))
);

COMMENT ON TABLE  sandbox_compare_report               IS 'Sandbox 精度对比报告表，汇总实验室与生产模型的全面对比，作为转正审核依据';
COMMENT ON COLUMN sandbox_compare_report.gate_passed   IS '是否通过全部转正门禁：精度提升>=2%，连续48小时，资源不超标，P99延迟不退化';
COMMENT ON COLUMN sandbox_compare_report.gate_detail_json IS '门禁详情JSON，结构：{accuracy_gate:{pass,delta}, resource_gate:{pass,mb_diff}, latency_gate:{pass,ms_diff}, hours_gate:{pass,hours}}';

CREATE INDEX idx_sb_report_session ON sandbox_compare_report (session_id);
CREATE INDEX idx_sb_report_scene   ON sandbox_compare_report (scene_id, generated_at DESC);

-- ============================================================
-- 4.3 Sandbox 转正日志表 sandbox_promote_log
-- ============================================================
CREATE TABLE sandbox_promote_log (
    id              BIGSERIAL    PRIMARY KEY,
    log_id          VARCHAR(64)  NOT NULL,
    session_id      VARCHAR(64)  NOT NULL,
    scene_id        VARCHAR(64)  NOT NULL,
    report_id       VARCHAR(64)  NOT NULL,
    promote_result  VARCHAR(16)  NOT NULL,
    promoted_by     VARCHAR(64)  NULL,
    reject_reason   VARCHAR(512) NULL,
    promoted_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_promote_log_id  UNIQUE (log_id),
    CONSTRAINT ck_promote_result  CHECK (promote_result IN ('APPROVED','REJECTED','ABORTED'))
);

COMMENT ON TABLE  sandbox_promote_log              IS 'Sandbox 转正操作日志，记录每次转正申请的审批结果';
COMMENT ON COLUMN sandbox_promote_log.promote_result IS 'APPROVED=审核通过转正，REJECTED=审核被拒，ABORTED=申请被撤回';
