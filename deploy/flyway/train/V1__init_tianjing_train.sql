-- ============================================================
-- Flyway 迁移脚本 V1
-- 数据库：tianjing_train（训练库）
-- Sprint：S0-03
-- 说明：创建训练库全部表
--       此库仅训练环境服务可访问；生产服务禁止连接此库
--       数据库连接串不得出现在生产服务任何配置中
-- ============================================================

-- ============================================================
-- 5.1 数据集表 dataset
-- ============================================================
CREATE TABLE dataset (
    id              BIGSERIAL    PRIMARY KEY,
    dataset_code    VARCHAR(64)  NOT NULL,
    dataset_name    VARCHAR(128) NOT NULL,
    scene_id        VARCHAR(64)  NOT NULL,
    factory_code    VARCHAR(32)  NOT NULL,
    source_type     VARCHAR(20)  NOT NULL,
    total_samples   INTEGER      NOT NULL DEFAULT 0,
    positive_samples INTEGER     NOT NULL DEFAULT 0,
    negative_samples INTEGER     NOT NULL DEFAULT 0,
    minio_prefix    VARCHAR(255) NOT NULL,
    is_deleted      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(64)  NOT NULL,
    updated_by      VARCHAR(64)  NOT NULL,
    version         INTEGER      NOT NULL DEFAULT 1,

    CONSTRAINT uk_dataset_code UNIQUE (dataset_code),
    CONSTRAINT ck_source_type  CHECK (source_type IN ('PRODUCTION_SYNC','MANUAL_COLLECT','AUGMENTED','LAB_REPLAY'))
);

COMMENT ON TABLE  dataset              IS '训练数据集元信息表（图像文件存储于 MinIO tianjing-datasets）';
COMMENT ON COLUMN dataset.source_type  IS '数据来源：PRODUCTION_SYNC=生产环境经光闸脱敏同步，MANUAL_COLLECT=人工现场采集，AUGMENTED=数据增强生成，LAB_REPLAY=实验室录像回放标注（V2.1，实验室阶段主要来源）';
COMMENT ON COLUMN dataset.minio_prefix IS 'MinIO 路径前缀，如 tianjing-datasets/sintering/SCENE-SINTER-005/2026-04/';

CREATE OR REPLACE FUNCTION fn_train_update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    NEW.version    = OLD.version + 1;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_dataset_updated_at
    BEFORE UPDATE ON dataset
    FOR EACH ROW EXECUTE FUNCTION fn_train_update_updated_at();

-- ============================================================
-- 5.1.1 数据集版本表 dataset_version
-- ============================================================
CREATE TABLE dataset_version (
    id               BIGSERIAL    PRIMARY KEY,
    version_id       VARCHAR(64)  NOT NULL,
    dataset_code     VARCHAR(64)  NOT NULL,
    version_tag      VARCHAR(32)  NOT NULL,
    sample_count     INTEGER      NOT NULL DEFAULT 0,
    minio_prefix     VARCHAR(255) NOT NULL,
    split_train      DECIMAL(5,4) NOT NULL DEFAULT 0.8,
    split_val        DECIMAL(5,4) NOT NULL DEFAULT 0.15,
    split_test       DECIMAL(5,4) NOT NULL DEFAULT 0.05,
    is_frozen        BOOLEAN      NOT NULL DEFAULT FALSE,
    is_deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(64)  NOT NULL,

    CONSTRAINT uk_dataset_version_id   UNIQUE (version_id),
    CONSTRAINT uk_dataset_version_tag  UNIQUE (dataset_code, version_tag),
    CONSTRAINT fk_dv_dataset           FOREIGN KEY (dataset_code) REFERENCES dataset(dataset_code)
);

COMMENT ON TABLE  dataset_version              IS '数据集版本表，每次数据集更新创建新版本，训练作业引用特定版本';
COMMENT ON COLUMN dataset_version.is_frozen    IS '是否冻结：冻结的版本不可修改，已被训练作业引用的版本自动冻结';
COMMENT ON COLUMN dataset_version.split_train  IS '训练集划分比例，默认 0.8（80%）';

-- ============================================================
-- 5.2 训练作业表 train_job
-- ============================================================
CREATE TABLE train_job (
    id                  BIGSERIAL     PRIMARY KEY,
    job_id              VARCHAR(64)   NOT NULL,
    plugin_id           VARCHAR(64)   NOT NULL,
    dataset_version_id  VARCHAR(64)   NOT NULL,
    trigger_type        VARCHAR(20)   NOT NULL,
    drift_metric_id     VARCHAR(64)   NULL,
    train_config_json   JSONB         NOT NULL,
    gpu_count           SMALLINT      NOT NULL DEFAULT 1,
    status              VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    best_epoch          INTEGER       NULL,
    best_map50          DECIMAL(6,4)  NULL,
    best_map50_95       DECIMAL(6,4)  NULL,
    mlflow_run_id       VARCHAR(64)   NULL,
    error_msg           TEXT          NULL,
    started_at          TIMESTAMPTZ   NULL,
    finished_at         TIMESTAMPTZ   NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(64)   NOT NULL,

    CONSTRAINT uk_job_id     UNIQUE (job_id),
    CONSTRAINT ck_job_trigger CHECK (trigger_type IN ('MANUAL','AUTO_DRIFT','SCHEDULED')),
    CONSTRAINT ck_job_status  CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED','CANCELLED')),
    CONSTRAINT fk_job_dv      FOREIGN KEY (dataset_version_id) REFERENCES dataset_version(version_id)
);

COMMENT ON COLUMN train_job.trigger_type    IS '触发方式：MANUAL=人工手动发起，AUTO_DRIFT=漂移监测自动触发（连续3天低于85%精度），SCHEDULED=定时训练';
COMMENT ON COLUMN train_job.train_config_json IS '训练配置JSON，结构：{epochs:200, batch_size:16, lr:0.01, img_size:640, augment:{}}';

CREATE INDEX idx_job_plugin_status ON train_job (plugin_id, status, created_at DESC);

-- ============================================================
-- 5.3 模型产物表 model_artifact
-- ============================================================
CREATE TABLE model_artifact (
    id                  BIGSERIAL     PRIMARY KEY,
    artifact_id         VARCHAR(64)   NOT NULL,
    job_id              VARCHAR(64)   NOT NULL,
    mlflow_run_id       VARCHAR(64)   NULL,
    model_path          VARCHAR(512)  NOT NULL,
    export_format       VARCHAR(16)   NOT NULL,
    map50               DECIMAL(6,4)  NULL,
    map50_95            DECIMAL(6,4)  NULL,
    precision_score     DECIMAL(6,4)  NULL,
    recall_score        DECIMAL(6,4)  NULL,
    inference_ms_gpu    DECIMAL(8,3)  NULL,
    inference_ms_cpu    DECIMAL(8,3)  NULL,
    model_size_mb       DECIMAL(10,2) NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(64)   NOT NULL,

    CONSTRAINT uk_artifact_id  UNIQUE (artifact_id),
    CONSTRAINT fk_artifact_job FOREIGN KEY (job_id) REFERENCES train_job(job_id),
    CONSTRAINT ck_art_format   CHECK (export_format IN ('ONNX','TENSORRT','TORCHSCRIPT','PYTORCH'))
);

COMMENT ON TABLE  model_artifact              IS '训练产出的模型文件元信息，对应 MLflow 实验中的模型版本';
COMMENT ON COLUMN model_artifact.model_path   IS 'MinIO 路径，格式：tianjing-models-staging/{plugin_id}/{version}/{filename}';

-- ============================================================
-- 5.4 标注任务表 annotation_task（Label Studio 任务映射）
-- ============================================================
CREATE TABLE annotation_task (
    id                   BIGSERIAL    PRIMARY KEY,
    task_id              VARCHAR(64)  NOT NULL,
    dataset_version_id   VARCHAR(64)  NOT NULL,
    label_studio_project_id INTEGER   NULL,
    task_name            VARCHAR(128) NOT NULL,
    annotation_type      VARCHAR(32)  NOT NULL,
    total_samples        INTEGER      NOT NULL DEFAULT 0,
    annotated_samples    INTEGER      NOT NULL DEFAULT 0,
    reviewed_samples     INTEGER      NOT NULL DEFAULT 0,
    status               VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    assigned_to          VARCHAR(64)  NULL,
    review_by            VARCHAR(64)  NULL,
    iou_threshold        DECIMAL(4,3) NOT NULL DEFAULT 0.75,
    started_at           TIMESTAMPTZ  NULL,
    finished_at          TIMESTAMPTZ  NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by           VARCHAR(64)  NOT NULL,

    CONSTRAINT uk_annotation_task_id UNIQUE (task_id),
    CONSTRAINT fk_anno_dv            FOREIGN KEY (dataset_version_id) REFERENCES dataset_version(version_id),
    CONSTRAINT ck_anno_type          CHECK (annotation_type IN ('DETECTION','SEGMENTATION','CLASSIFICATION')),
    CONSTRAINT ck_anno_status        CHECK (status IN ('PENDING','IN_PROGRESS','REVIEWING','COMPLETED'))
);

COMMENT ON TABLE  annotation_task                   IS '数据标注任务表，与 Label Studio 标注项目映射';
COMMENT ON COLUMN annotation_task.iou_threshold     IS '标注一致性校验阈值，两标注员 IOU > 0.75 则通过交叉复核';
