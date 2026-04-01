-- ============================================================
-- Flyway 迁移脚本 V1
-- 数据库：tianjing_prod（生产库）
-- Sprint：S0-03
-- 说明：创建生产库全部 18 张表（含分区表及子分区）
--       触发器、索引、约束全部在同一脚本中创建
-- ============================================================

-- ============================================================
-- 通用函数：自动维护 updated_at 字段（所有非分区表共用）
-- ============================================================
CREATE OR REPLACE FUNCTION fn_update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    NEW.version    = OLD.version + 1;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION fn_update_updated_at() IS '通用触发器函数：自动更新 updated_at 并递增 version 乐观锁字段';

-- ============================================================
-- 3.1 厂部表 factory
-- ============================================================
CREATE TABLE factory (
    id           BIGSERIAL     PRIMARY KEY,
    factory_code VARCHAR(32)   NOT NULL,
    factory_name VARCHAR(64)   NOT NULL,
    factory_type VARCHAR(32)   NOT NULL,
    sort_order   SMALLINT      NOT NULL DEFAULT 0,
    remark       VARCHAR(255)  NULL,
    is_deleted   BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by   VARCHAR(64)   NOT NULL,
    updated_by   VARCHAR(64)   NOT NULL,
    version      INTEGER       NOT NULL DEFAULT 1,

    CONSTRAINT uk_factory_code UNIQUE (factory_code),
    CONSTRAINT ck_factory_type CHECK (factory_type IN ('RAW_MATERIAL','SMELTING','ROLLING'))
);

COMMENT ON TABLE  factory              IS '厂部基础信息表';
COMMENT ON COLUMN factory.factory_code IS '厂部编码，全局唯一，取值：PELLET（球团）/ SINTER（烧结）/ STEEL（炼钢）/ SECTION（型钢）/ STRIP（带钢）';
COMMENT ON COLUMN factory.factory_type IS '厂部类型：RAW_MATERIAL=原料加工，SMELTING=冶炼，ROLLING=轧制';
COMMENT ON COLUMN factory.sort_order   IS '页面展示排序，数字越小越靠前';

CREATE TRIGGER trg_factory_updated_at
    BEFORE UPDATE ON factory
    FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at();

-- ============================================================
-- 3.2 工序表 process
-- ============================================================
CREATE TABLE process (
    id           BIGSERIAL    PRIMARY KEY,
    process_code VARCHAR(64)  NOT NULL,
    process_name VARCHAR(128) NOT NULL,
    factory_code VARCHAR(32)  NOT NULL,
    sort_order   SMALLINT     NOT NULL DEFAULT 0,
    is_deleted   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by   VARCHAR(64)  NOT NULL,
    updated_by   VARCHAR(64)  NOT NULL,
    version      INTEGER      NOT NULL DEFAULT 1,

    CONSTRAINT uk_process_code    UNIQUE (process_code),
    CONSTRAINT fk_process_factory FOREIGN KEY (factory_code)
        REFERENCES factory(factory_code) ON UPDATE CASCADE
);

COMMENT ON TABLE  process              IS '生产工序表，隶属于厂部';
COMMENT ON COLUMN process.process_code IS '工序编码，全局唯一';
COMMENT ON COLUMN process.factory_code IS '所属厂部编码，外键关联 factory.factory_code';

CREATE TRIGGER trg_process_updated_at
    BEFORE UPDATE ON process
    FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at();

-- ============================================================
-- 3.3 场景配置表 scene_config
-- ============================================================
CREATE TABLE scene_config (
    id               BIGSERIAL     PRIMARY KEY,
    scene_id         VARCHAR(64)   NOT NULL,
    scene_name       VARCHAR(128)  NOT NULL,
    factory_code     VARCHAR(32)   NOT NULL,
    process_code     VARCHAR(64)   NOT NULL,
    category         VARCHAR(32)   NOT NULL,
    priority         VARCHAR(8)    NOT NULL DEFAULT 'P3',
    status           VARCHAR(16)   NOT NULL DEFAULT 'DRAFT',
    workflow_json    JSONB         NOT NULL DEFAULT '{}',
    algo_config_json JSONB         NOT NULL DEFAULT '{}',
    alarm_config_json JSONB        NOT NULL DEFAULT '{}',
    roi_config_json  JSONB         NOT NULL DEFAULT '{}',
    sandbox_enabled  BOOLEAN       NOT NULL DEFAULT FALSE,
    sandbox_model_id VARCHAR(64)   NULL,
    prod_model_id    VARCHAR(64)   NULL,
    frame_interval   SMALLINT      NOT NULL DEFAULT 5,
    is_deleted       BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(64)   NOT NULL,
    updated_by       VARCHAR(64)   NOT NULL,
    version          INTEGER       NOT NULL DEFAULT 1,

    CONSTRAINT uk_scene_id         UNIQUE (scene_id),
    CONSTRAINT fk_scene_factory    FOREIGN KEY (factory_code) REFERENCES factory(factory_code),
    CONSTRAINT fk_scene_process    FOREIGN KEY (process_code) REFERENCES process(process_code),
    CONSTRAINT ck_scene_category   CHECK (category IN ('QUALITY_INSPECT','EQUIPMENT_MONITOR','PROCESS_PARAM')),
    CONSTRAINT ck_scene_priority   CHECK (priority IN ('P1','P2','P3','P4')),
    CONSTRAINT ck_scene_status     CHECK (status IN ('DRAFT','ACTIVE','INACTIVE','SANDBOX_ONLY'))
);

COMMENT ON TABLE  scene_config                  IS '视觉识别场景配置主表，每行对应一个完整的视觉检测场景';
COMMENT ON COLUMN scene_config.scene_id         IS '场景业务主键，格式 SCENE-{厂部缩写}-{3位序号}，如 SCENE-SINTER-005';
COMMENT ON COLUMN scene_config.category         IS '场景功能分类：QUALITY_INSPECT=质量检测，EQUIPMENT_MONITOR=设备状态监测，PROCESS_PARAM=工艺参数检测';
COMMENT ON COLUMN scene_config.priority         IS '需求优先级：P1=立即实施，P2=重点规划，P3=按序推进，P4=后期迭代';
COMMENT ON COLUMN scene_config.status           IS '场景状态：DRAFT=草稿未上线，ACTIVE=生产运行，INACTIVE=已下线，SANDBOX_ONLY=仅实验室模式';
COMMENT ON COLUMN scene_config.workflow_json    IS '低代码编排器产生的 JSON，结构为 {nodes:[{id,type,params}], edges:[{source,target,label}]}';
COMMENT ON COLUMN scene_config.algo_config_json IS '算法配置 JSON，结构为 {plugin_id, conf_threshold, iou_threshold, extra:{}}';
COMMENT ON COLUMN scene_config.alarm_config_json IS '告警配置 JSON，结构为 {level, confirm_frames, suppress_seconds, push_channels:[]}';
COMMENT ON COLUMN scene_config.roi_config_json  IS 'ROI 区域配置 JSON，结构为 {regions:[{name,x,y,w,h}], calibration_id}';
COMMENT ON COLUMN scene_config.sandbox_enabled  IS '是否启用 Sandbox 并行推理。启用时 traffic-mirror-service 开始分流';
COMMENT ON COLUMN scene_config.frame_interval   IS '推理帧间隔：1=每帧推理，5=每5帧推理一次';
COMMENT ON COLUMN scene_config.version          IS '乐观锁版本号，每次配置变更递增，用于并发控制';

CREATE TRIGGER trg_scene_config_updated_at
    BEFORE UPDATE ON scene_config
    FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at();

-- 路由引擎按 scene_id 查询（最核心，部分索引加速）
CREATE UNIQUE INDEX idx_scene_scene_id ON scene_config (scene_id)
    WHERE is_deleted = FALSE;

-- 按厂部+工序筛选场景列表
CREATE INDEX idx_scene_factory_process ON scene_config (factory_code, process_code)
    WHERE is_deleted = FALSE;

-- ============================================================
-- 3.4 场景配置变更历史表 scene_config_history
-- ============================================================
CREATE TABLE scene_config_history (
    id            BIGSERIAL    PRIMARY KEY,
    scene_id      VARCHAR(64)  NOT NULL,
    config_version INTEGER     NOT NULL,
    snapshot_json JSONB        NOT NULL,
    change_type   VARCHAR(16)  NOT NULL,
    change_desc   VARCHAR(255) NULL,
    changed_by    VARCHAR(64)  NOT NULL,
    changed_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_history_scene FOREIGN KEY (scene_id) REFERENCES scene_config(scene_id),
    CONSTRAINT ck_history_change_type CHECK (change_type IN ('CREATE','UPDATE','ENABLE','DISABLE','ROLLBACK'))
);

COMMENT ON TABLE  scene_config_history               IS '场景配置变更历史表，支持版本回溯';
COMMENT ON COLUMN scene_config_history.snapshot_json IS '变更前的完整配置快照，JSON 格式存储 scene_config 全字段';
COMMENT ON COLUMN scene_config_history.change_type   IS '变更类型：CREATE=新建，UPDATE=修改，ENABLE=启用，DISABLE=停用，ROLLBACK=回滚';

CREATE INDEX idx_history_scene_id ON scene_config_history (scene_id, changed_at DESC);

-- ============================================================
-- 3.5 摄像头设备表 camera_device
-- ============================================================
CREATE TABLE camera_device (
    id                 BIGSERIAL    PRIMARY KEY,
    device_code        VARCHAR(64)  NOT NULL,
    scene_id           VARCHAR(64)  NOT NULL,
    device_name        VARCHAR(128) NOT NULL,
    ip_address         VARCHAR(45)  NOT NULL,
    mac_address        VARCHAR(17)  NULL,
    vendor             VARCHAR(32)  NULL,
    firmware_version   VARCHAR(32)  NULL,
    rtsp_url           VARCHAR(512) NULL,
    protocol           VARCHAR(16)  NOT NULL DEFAULT 'RTSP',
    resolution_width   SMALLINT     NOT NULL DEFAULT 1920,
    resolution_height  SMALLINT     NOT NULL DEFAULT 1080,
    fps                SMALLINT     NOT NULL DEFAULT 25,
    location_desc      VARCHAR(255) NULL,
    edge_node_id       VARCHAR(64)  NULL,
    health_status      VARCHAR(16)  NOT NULL DEFAULT 'UNKNOWN',
    last_health_check  TIMESTAMPTZ  NULL,
    health_score       SMALLINT     NULL,
    is_supplement_light BOOLEAN     NOT NULL DEFAULT FALSE,
    is_deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by         VARCHAR(64)  NOT NULL,
    updated_by         VARCHAR(64)  NOT NULL,
    version            INTEGER      NOT NULL DEFAULT 1,

    -- 一个场景只绑定一个主摄像头
    CONSTRAINT uk_camera_scene     UNIQUE (scene_id),
    CONSTRAINT fk_camera_scene     FOREIGN KEY (scene_id) REFERENCES scene_config(scene_id),
    CONSTRAINT ck_camera_protocol  CHECK (protocol IN ('RTSP','GB28181')),
    CONSTRAINT ck_camera_vendor    CHECK (vendor IN ('HIKVISION','DAHUA','UNIVIEW','OTHER') OR vendor IS NULL),
    CONSTRAINT ck_camera_health    CHECK (health_status IN ('HEALTHY','BLURRY','OFFLINE','SHIFTED','UNKNOWN'))
);

-- 部分索引：仅对未软删除的记录强制 device_code 唯一，允许同编码重注册
CREATE UNIQUE INDEX uq_camera_device_code_active
    ON camera_device (device_code)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE  camera_device                       IS '摄像头物理设备信息表，与场景一一绑定';
COMMENT ON COLUMN camera_device.mac_address           IS 'MAC 地址，格式 AA:BB:CC:DD:EE:FF。用于工业防火墙白名单接入控制，NULL=未录入';
COMMENT ON COLUMN camera_device.vendor                IS '设备厂商：HIKVISION=海康威视，DAHUA=大华，UNIVIEW=宇视，OTHER=其他';
COMMENT ON COLUMN camera_device.rtsp_url              IS 'RTSP 完整 URL，含用户名密码，入库前必须 AES-256 加密，读取时解密';
COMMENT ON COLUMN camera_device.health_status         IS '健康状态：HEALTHY=正常，BLURRY=图像模糊，OFFLINE=离线/黑屏，SHIFTED=相机偏移，UNKNOWN=未检测';
COMMENT ON COLUMN camera_device.health_score          IS '图像质量综合评分 0-100，由拉普拉斯方差计算，低于 50 触发告警';
COMMENT ON COLUMN camera_device.is_supplement_light   IS '是否安装补光灯，影响黑暗环境下的健康检测基准亮度阈值';

CREATE TRIGGER trg_camera_device_updated_at
    BEFORE UPDATE ON camera_device
    FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at();

-- ============================================================
-- 3.6 摄像头健康记录表 camera_health_record（按月分区）
-- ============================================================
CREATE TABLE camera_health_record (
    id              BIGSERIAL    NOT NULL,
    device_code     VARCHAR(64)  NOT NULL,
    scene_id        VARCHAR(64)  NOT NULL,
    check_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    laplacian_var   DECIMAL(10,4) NULL,
    avg_brightness  SMALLINT     NULL,
    optical_flow_dx DECIMAL(8,4) NULL,
    optical_flow_dy DECIMAL(8,4) NULL,
    health_score    SMALLINT     NOT NULL,
    fault_type      VARCHAR(16)  NULL,
    fault_desc      VARCHAR(255) NULL,
    action_taken    VARCHAR(32)  NULL,
    work_order_id   VARCHAR(64)  NULL,

    PRIMARY KEY (id, check_at)
) PARTITION BY RANGE (check_at);

CREATE TABLE camera_health_record_2026_04 PARTITION OF camera_health_record
    FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');
CREATE TABLE camera_health_record_2026_05 PARTITION OF camera_health_record
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
CREATE TABLE camera_health_record_2026_06 PARTITION OF camera_health_record
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');

COMMENT ON TABLE  camera_health_record               IS '摄像头健康检测历史记录，按月分区，保留30天';
COMMENT ON COLUMN camera_health_record.laplacian_var IS '拉普拉斯方差，用于模糊检测。阈值<100判定为模糊，<50判定为严重模糊';
COMMENT ON COLUMN camera_health_record.avg_brightness IS '帧平均灰度值：<30=黑屏/过暗，>220=过曝，正常范围50-200';
COMMENT ON COLUMN camera_health_record.action_taken  IS '已采取动作：WORK_ORDER=派发维修工单，PAUSE_INFER=暂停推理，RECALIBRATE=推送重标定工单';

CREATE INDEX idx_health_device_check ON camera_health_record (device_code, check_at DESC);
CREATE INDEX idx_health_scene_check  ON camera_health_record (scene_id, check_at DESC);

-- ============================================================
-- 3.7 标定记录表 calibration_record
-- ============================================================
CREATE TABLE calibration_record (
    id              BIGSERIAL     PRIMARY KEY,
    calibration_id  VARCHAR(64)   NOT NULL,
    scene_id        VARCHAR(64)   NOT NULL,
    device_code     VARCHAR(64)   NOT NULL,
    ref_distance_mm DECIMAL(10,3) NOT NULL,
    pixel_p1_x      INTEGER       NOT NULL,
    pixel_p1_y      INTEGER       NOT NULL,
    pixel_p2_x      INTEGER       NOT NULL,
    pixel_p2_y      INTEGER       NOT NULL,
    pixel_distance  DECIMAL(10,4) NOT NULL,
    scale_mm_per_px DECIMAL(12,6) NOT NULL,
    is_active       BOOLEAN       NOT NULL DEFAULT TRUE,
    calibrated_by   VARCHAR(64)   NOT NULL,
    remark          VARCHAR(255)  NULL,
    is_deleted      BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(64)   NOT NULL,
    updated_by      VARCHAR(64)   NOT NULL,
    version         INTEGER       NOT NULL DEFAULT 1,

    CONSTRAINT uk_calibration_id  UNIQUE (calibration_id),
    CONSTRAINT fk_calib_scene     FOREIGN KEY (scene_id) REFERENCES scene_config(scene_id)
);

COMMENT ON TABLE  calibration_record               IS '在线标定记录表，记录像素与物理距离的换算比例尺';
COMMENT ON COLUMN calibration_record.scale_mm_per_px IS '核心标定结果：每像素对应的物理距离（毫米）';
COMMENT ON COLUMN calibration_record.is_active     IS '是否当前生效的标定记录。同一场景只有一条 is_active=true';

CREATE INDEX idx_calib_scene_active ON calibration_record (scene_id, is_active)
    WHERE is_deleted = FALSE;

CREATE TRIGGER trg_calibration_updated_at
    BEFORE UPDATE ON calibration_record
    FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at();

-- ============================================================
-- 3.8 算法插件表 algorithm_plugin
-- ============================================================
CREATE TABLE algorithm_plugin (
    id               BIGSERIAL     PRIMARY KEY,
    plugin_id        VARCHAR(64)   NOT NULL UNIQUE,  -- 列级唯一约束，满足自引用 FK 的参照要求
    plugin_name      VARCHAR(128)  NOT NULL,
    plugin_type      VARCHAR(32)   NOT NULL,
    is_atom          BOOLEAN       NOT NULL DEFAULT TRUE,
    parent_plugin_id VARCHAR(64)   NULL,
    version          VARCHAR(16)   NOT NULL,
    backbone         VARCHAR(64)   NULL,
    metadata_json    JSONB         NOT NULL DEFAULT '{}',
    ui_schema_json   JSONB         NOT NULL DEFAULT '{}',
    infer_backend    VARCHAR(16)   NOT NULL DEFAULT 'LOCAL_GPU',
    status           VARCHAR(16)   NOT NULL DEFAULT 'REGISTERED',
    current_model_version_id VARCHAR(64) NULL,
    is_deleted       BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(64)   NOT NULL,
    updated_by       VARCHAR(64)   NOT NULL,
    version_lock     INTEGER       NOT NULL DEFAULT 1,

    CONSTRAINT ck_plugin_type      CHECK (plugin_type IN ('DETECTION','SEGMENTATION','CLASSIFICATION','MEASUREMENT','ENHANCEMENT')),
    CONSTRAINT ck_plugin_status    CHECK (status IN ('REGISTERED','ACTIVE','DEPRECATED')),
    CONSTRAINT ck_infer_backend    CHECK (infer_backend IN ('LOCAL_GPU','CLOUD_API','ONNX_CPU')),
    CONSTRAINT fk_plugin_parent    FOREIGN KEY (parent_plugin_id) REFERENCES algorithm_plugin(plugin_id)
);

COMMENT ON TABLE  algorithm_plugin                  IS '算法插件注册表，包含原子算法和场景任务头两类';
COMMENT ON COLUMN algorithm_plugin.plugin_id        IS '插件全局唯一ID，格式：ATOM-DETECT-YOLO-V1 或 HEAD-SIDEPLATE-V1';
COMMENT ON COLUMN algorithm_plugin.is_atom          IS '是否原子算法：true=原子层（共享主干），false=任务头层（场景微调）';
COMMENT ON COLUMN algorithm_plugin.infer_backend    IS '推理后端类型（V2.1）：LOCAL_GPU=本地TensorRT；CLOUD_API=云端视觉API；ONNX_CPU=CPU ONNX降级';
COMMENT ON COLUMN algorithm_plugin.ui_schema_json   IS '前端低代码编排器属性面板动态表单渲染 Schema（JSON Schema Draft-07 子集）';

-- 注意：algorithm_plugin 不用 fn_update_updated_at（version 字段名为 version_lock，避免冲突）
CREATE OR REPLACE FUNCTION fn_plugin_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at   = NOW();
    NEW.version_lock = OLD.version_lock + 1;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_plugin_updated_at
    BEFORE UPDATE ON algorithm_plugin
    FOR EACH ROW EXECUTE FUNCTION fn_plugin_updated_at();

-- ============================================================
-- 3.9 模型版本表 model_version
-- ============================================================
CREATE TABLE model_version (
    id                  BIGSERIAL     PRIMARY KEY,
    version_id          VARCHAR(64)   NOT NULL,
    plugin_id           VARCHAR(64)   NOT NULL,
    mlflow_run_id       VARCHAR(64)   NULL,
    mlflow_model_uri    VARCHAR(512)  NULL,
    model_path          VARCHAR(512)  NOT NULL,
    export_format       VARCHAR(16)   NOT NULL,
    map50               DECIMAL(6,4)  NULL,
    map50_95            DECIMAL(6,4)  NULL,
    precision_score     DECIMAL(6,4)  NULL,
    recall_score        DECIMAL(6,4)  NULL,
    inference_ms_gpu    DECIMAL(8,3)  NULL,
    inference_ms_cpu    DECIMAL(8,3)  NULL,
    model_size_mb       DECIMAL(10,2) NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'STAGING',
    sandbox_hours       INTEGER       NOT NULL DEFAULT 0,
    sandbox_precision   DECIMAL(6,4)  NULL,
    sandbox_recall      DECIMAL(6,4)  NULL,
    approved_by         VARCHAR(64)   NULL,
    approved_at         TIMESTAMPTZ   NULL,
    deployed_at         TIMESTAMPTZ   NULL,
    deprecated_at       TIMESTAMPTZ   NULL,
    train_job_id        VARCHAR(64)   NULL,
    is_deleted          BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(64)   NOT NULL,
    updated_by          VARCHAR(64)   NOT NULL,
    version             INTEGER       NOT NULL DEFAULT 1,

    CONSTRAINT uk_model_version_id UNIQUE (version_id),
    CONSTRAINT fk_model_plugin     FOREIGN KEY (plugin_id) REFERENCES algorithm_plugin(plugin_id),
    CONSTRAINT ck_model_format     CHECK (export_format IN ('ONNX','TENSORRT','TORCHSCRIPT')),
    CONSTRAINT ck_model_status     CHECK (status IN ('STAGING','SANDBOX_VALIDATING','REVIEWING','PRODUCTION','DEPRECATED'))
);

COMMENT ON TABLE  model_version                   IS '模型版本管理表，记录从训练产出到生产部署的完整生命周期';
COMMENT ON COLUMN model_version.status            IS '状态流转：STAGING→SANDBOX_VALIDATING→REVIEWING→PRODUCTION，或任意状态→DEPRECATED';
COMMENT ON COLUMN model_version.sandbox_hours     IS 'Sandbox 验证累计小时数，须 >= 48 小时且精度达标，方可进入 REVIEWING';
COMMENT ON COLUMN model_version.approved_by       IS '必须由具有 MODEL_REVIEWER 角色的用户审核，且不能与模型提交者为同一人（四眼原则）';

CREATE INDEX idx_model_plugin_status ON model_version (plugin_id, status)
    WHERE is_deleted = FALSE;

CREATE TRIGGER trg_model_version_updated_at
    BEFORE UPDATE ON model_version
    FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at();

-- ============================================================
-- 3.10 告警规则表 alarm_rule
-- ============================================================
CREATE TABLE alarm_rule (
    id                  BIGSERIAL    PRIMARY KEY,
    rule_id             VARCHAR(64)  NOT NULL,
    scene_id            VARCHAR(64)  NOT NULL,
    alarm_level         VARCHAR(16)  NOT NULL,
    conf_threshold      DECIMAL(5,4) NOT NULL DEFAULT 0.85,
    confirm_frames      SMALLINT     NOT NULL DEFAULT 3,
    suppress_seconds    INTEGER      NOT NULL DEFAULT 300,
    push_channels       VARCHAR(32)[] NOT NULL DEFAULT ARRAY['IIOT_PLATFORM'],
    scada_signal        BOOLEAN      NOT NULL DEFAULT FALSE,
    init_mode           VARCHAR(16)  NOT NULL DEFAULT 'SUGGEST',
    is_deleted          BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(64)  NOT NULL,
    updated_by          VARCHAR(64)  NOT NULL,
    version             INTEGER      NOT NULL DEFAULT 1,

    CONSTRAINT uk_rule_id        UNIQUE (rule_id),
    CONSTRAINT uk_rule_scene     UNIQUE (scene_id),
    CONSTRAINT fk_rule_scene     FOREIGN KEY (scene_id) REFERENCES scene_config(scene_id),
    CONSTRAINT ck_rule_level     CHECK (alarm_level IN ('INFO','WARNING','CRITICAL')),
    CONSTRAINT ck_rule_init_mode CHECK (init_mode IN ('SUGGEST','ENFORCE'))
);

COMMENT ON TABLE  alarm_rule                    IS '场景告警判定规则表，控制告警触发条件和推送方式';
COMMENT ON COLUMN alarm_rule.confirm_frames     IS '连续触发帧数阈值，避免单帧噪声误报';
COMMENT ON COLUMN alarm_rule.suppress_seconds   IS '告警抑制窗口（秒），窗口内同类告警只推送一次，防告警风暴';
COMMENT ON COLUMN alarm_rule.push_channels      IS '推送通道数组：IIOT_PLATFORM=工业互联网，WECHAT_WORK=企业微信，SMS=短信，WEBSOCKET=Web推送';
COMMENT ON COLUMN alarm_rule.init_mode          IS '初期运行模式：SUGGEST=建议模式（AI只报警，不触发联锁），ENFORCE=执行模式（可联动SCADA）';

CREATE TRIGGER trg_alarm_rule_updated_at
    BEFORE UPDATE ON alarm_rule
    FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at();

-- ============================================================
-- 3.11 告警记录表 alarm_record（按月分区）
-- ============================================================
CREATE TABLE alarm_record (
    id              BIGSERIAL     NOT NULL,
    alarm_id        VARCHAR(64)   NOT NULL,
    scene_id        VARCHAR(64)   NOT NULL,
    factory_code    VARCHAR(32)   NOT NULL,
    alarm_level     VARCHAR(16)   NOT NULL,
    anomaly_type    VARCHAR(64)   NOT NULL,
    confidence      DECIMAL(5,4)  NOT NULL,
    bbox_json       JSONB         NULL,
    measurement_val DECIMAL(10,3) NULL,
    measurement_unit VARCHAR(16)  NULL,
    image_url       VARCHAR(512)  NOT NULL,
    frame_id        VARCHAR(64)   NOT NULL,
    plugin_id       VARCHAR(64)   NOT NULL,
    model_version_id VARCHAR(64)  NOT NULL,
    -- SECURITY: 安全冗余字段，alarm-judge-service 的 Sandbox 拦截器确保此表永远不会有 is_sandbox=true 的记录
    is_sandbox      BOOLEAN       NOT NULL DEFAULT FALSE,
    confirm_frames  SMALLINT      NOT NULL,
    push_status     VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    push_channels_json JSONB      NULL,
    alarm_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    resolved_at     TIMESTAMPTZ   NULL,

    PRIMARY KEY (id, alarm_at)
) PARTITION BY RANGE (alarm_at);

CREATE TABLE alarm_record_2026_04 PARTITION OF alarm_record
    FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');
CREATE TABLE alarm_record_2026_05 PARTITION OF alarm_record
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
CREATE TABLE alarm_record_2026_06 PARTITION OF alarm_record
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');

COMMENT ON TABLE  alarm_record                  IS '生产告警记录表，仅记录生产环境（is_sandbox=false）的告警，按月分区';
COMMENT ON COLUMN alarm_record.alarm_id         IS '告警全局唯一ID，格式：ALM-20260415-000001';
COMMENT ON COLUMN alarm_record.is_sandbox       IS '安全冗余字段，alarm-judge-service 的 Sandbox 拦截器确保此表中永远不会有 is_sandbox=true 的记录';
COMMENT ON COLUMN alarm_record.push_status      IS '推送状态：PENDING=待推送，SENT=已推送至IIoT平台，FAILED=推送失败';

CREATE INDEX idx_alarm_scene_at   ON alarm_record (scene_id, alarm_at DESC);
CREATE INDEX idx_alarm_factory_at ON alarm_record (factory_code, alarm_at DESC);
CREATE INDEX idx_alarm_level_at   ON alarm_record (alarm_level, alarm_at DESC);

-- ============================================================
-- 3.12 告警派发表 alarm_dispatch
-- ============================================================
CREATE TABLE alarm_dispatch (
    id                BIGSERIAL    PRIMARY KEY,
    dispatch_id       VARCHAR(64)  NOT NULL,
    alarm_id          VARCHAR(64)  NOT NULL,
    iiot_work_order_id VARCHAR(64) NULL,
    assigned_to       VARCHAR(64)  NULL,
    assigned_dept     VARCHAR(64)  NULL,
    dispatch_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expected_resolve_at TIMESTAMPTZ NULL,
    status            VARCHAR(16)  NOT NULL DEFAULT 'DISPATCHED',
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by        VARCHAR(64)  NOT NULL,
    updated_by        VARCHAR(64)  NOT NULL,
    version           INTEGER      NOT NULL DEFAULT 1,

    CONSTRAINT uk_dispatch_id   UNIQUE (dispatch_id),
    CONSTRAINT ck_dispatch_status CHECK (status IN ('DISPATCHED','CONFIRMED','RESOLVED','CANCELLED'))
);

COMMENT ON TABLE  alarm_dispatch                       IS '告警工单派发记录，对接工业互联网平台的工单系统';
COMMENT ON COLUMN alarm_dispatch.iiot_work_order_id   IS '工业互联网平台生成的工单ID，用于跨系统追踪处置状态';

CREATE INDEX idx_dispatch_alarm ON alarm_dispatch (alarm_id);

CREATE TRIGGER trg_alarm_dispatch_updated_at
    BEFORE UPDATE ON alarm_dispatch
    FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at();

-- ============================================================
-- 3.13 告警反馈表 alarm_feedback
-- ============================================================
CREATE TABLE alarm_feedback (
    id               BIGSERIAL     PRIMARY KEY,
    feedback_id      VARCHAR(64)   NOT NULL,
    alarm_id         VARCHAR(64)   NOT NULL,
    dispatch_id      VARCHAR(64)   NOT NULL,
    scene_id         VARCHAR(64)   NOT NULL,
    feedback_result  VARCHAR(16)   NOT NULL,
    feedback_desc    VARCHAR(512)  NULL,
    actual_anomaly_type VARCHAR(64) NULL,
    feedback_by      VARCHAR(64)   NOT NULL,
    feedback_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    is_deleted       BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(64)   NOT NULL,

    CONSTRAINT uk_feedback_id      UNIQUE (feedback_id),
    CONSTRAINT ck_feedback_result  CHECK (feedback_result IN ('TRUE_POSITIVE','FALSE_POSITIVE','FALSE_NEGATIVE'))
);

COMMENT ON TABLE  alarm_feedback                     IS '告警人工处置反馈表，岗位人员处置工单后回传，驱动模型漂移监测';
COMMENT ON COLUMN alarm_feedback.feedback_result     IS 'TRUE_POSITIVE=确认真实异常，FALSE_POSITIVE=AI误报，FALSE_NEGATIVE=AI漏检';
COMMENT ON COLUMN alarm_feedback.actual_anomaly_type IS '当 AI 误报或误判类型时，人工填写实际异常类型，用于补充训练样本';

CREATE INDEX idx_feedback_scene_at ON alarm_feedback (scene_id, feedback_at DESC);

-- ============================================================
-- 3.14 模型漂移指标表 drift_metric
-- ============================================================
CREATE TABLE drift_metric (
    id                  BIGSERIAL     PRIMARY KEY,
    metric_id           VARCHAR(64)   NOT NULL,
    scene_id            VARCHAR(64)   NOT NULL,
    model_version_id    VARCHAR(64)   NOT NULL,
    metric_date         DATE          NOT NULL,
    total_alarms        INTEGER       NOT NULL DEFAULT 0,
    tp_count            INTEGER       NOT NULL DEFAULT 0,
    fp_count            INTEGER       NOT NULL DEFAULT 0,
    fn_count            INTEGER       NOT NULL DEFAULT 0,
    precision_val       DECIMAL(6,4)  NULL,
    recall_val          DECIMAL(6,4)  NULL,
    f1_score            DECIMAL(6,4)  NULL,
    feedback_coverage   DECIMAL(6,4)  NULL,
    is_below_threshold  BOOLEAN       NOT NULL DEFAULT FALSE,
    retrain_triggered   BOOLEAN       NOT NULL DEFAULT FALSE,
    retrain_job_id      VARCHAR(64)   NULL,
    consecutive_below   SMALLINT      NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by          VARCHAR(64)   NOT NULL DEFAULT 'SYSTEM',

    CONSTRAINT uk_drift_scene_date   UNIQUE (scene_id, metric_date),
    CONSTRAINT uk_drift_metric_id    UNIQUE (metric_id),
    CONSTRAINT fk_drift_scene        FOREIGN KEY (scene_id) REFERENCES scene_config(scene_id)
);

COMMENT ON TABLE  drift_metric                     IS '模型精度漂移每日统计表，由 drift-monitor-service 定时计算';
COMMENT ON COLUMN drift_metric.feedback_coverage   IS '人工反馈覆盖率，覆盖率过低时精度计算结果不可靠';
COMMENT ON COLUMN drift_metric.consecutive_below   IS '连续低于精度阈值天数，达到3天自动触发重训练任务';

CREATE INDEX idx_drift_scene_date ON drift_metric (scene_id, metric_date DESC);

-- ============================================================
-- 3.15 用户与权限表（sys_user / sys_role / sys_user_role）
-- ============================================================
CREATE TABLE sys_user (
    id           BIGSERIAL    PRIMARY KEY,
    user_id      VARCHAR(64)  NOT NULL,
    username     VARCHAR(64)  NOT NULL,
    display_name VARCHAR(64)  NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    dept_code    VARCHAR(64)  NULL,
    phone        VARCHAR(20)  NULL,
    email        VARCHAR(128) NULL,
    last_login_at        TIMESTAMPTZ  NULL,
    status               VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    failed_login_count   INTEGER      NOT NULL DEFAULT 0,
    password_changed_at  TIMESTAMPTZ  NULL,
    is_locked            BOOLEAN      NOT NULL DEFAULT FALSE,
    is_deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by           VARCHAR(64)  NOT NULL,
    updated_by           VARCHAR(64)  NOT NULL,
    version              INTEGER      NOT NULL DEFAULT 1,

    CONSTRAINT uk_user_id   UNIQUE (user_id),
    CONSTRAINT uk_username  UNIQUE (username),
    CONSTRAINT ck_user_status CHECK (status IN ('ACTIVE','LOCKED','INACTIVE'))
);

COMMENT ON TABLE  sys_user               IS '系统用户表，对应平台管理后台的登录用户';
COMMENT ON COLUMN sys_user.password_hash IS 'BCrypt 哈希值，cost factor=12，禁止明文存储';
COMMENT ON COLUMN sys_user.phone         IS '手机号，AES-256 加密存储，用于短信告警推送';

CREATE TRIGGER trg_sys_user_updated_at
    BEFORE UPDATE ON sys_user
    FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at();

CREATE TABLE sys_role (
    id          BIGSERIAL    PRIMARY KEY,
    role_code   VARCHAR(64)  NOT NULL,
    role_name   VARCHAR(64)  NOT NULL,
    description VARCHAR(255) NULL,
    is_deleted  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by  VARCHAR(64)  NOT NULL,

    CONSTRAINT uk_role_code UNIQUE (role_code)
);

COMMENT ON COLUMN sys_role.role_code IS '角色编码：ADMIN=超级管理员，SCENE_EDITOR=场景编辑，MODEL_REVIEWER=模型审核（四眼原则），SANDBOX_OPERATOR=实验室操作，VIEWER=只读查看';

CREATE TABLE sys_user_role (
    id         BIGSERIAL    PRIMARY KEY,
    user_id    VARCHAR(64)  NOT NULL,
    role_code  VARCHAR(64)  NOT NULL,
    granted_by VARCHAR(64)  NOT NULL,
    granted_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expired_at TIMESTAMPTZ  NULL,

    CONSTRAINT uk_user_role       UNIQUE (user_id, role_code),
    CONSTRAINT fk_ur_user         FOREIGN KEY (user_id)   REFERENCES sys_user(user_id),
    CONSTRAINT fk_ur_role         FOREIGN KEY (role_code) REFERENCES sys_role(role_code)
);

-- ============================================================
-- 3.15.4 操作审计日志表 sys_operation_log（按月分区）
-- ============================================================
CREATE TABLE sys_operation_log (
    id           BIGSERIAL     NOT NULL,
    log_id       VARCHAR(64)   NOT NULL,
    operator     VARCHAR(64)   NOT NULL,
    operation    VARCHAR(64)   NOT NULL,
    target_type  VARCHAR(32)   NOT NULL,
    target_id    VARCHAR(64)   NOT NULL,
    before_json  JSONB         NULL,
    after_json   JSONB         NULL,
    ip_address   VARCHAR(45)   NULL,
    trace_id     VARCHAR(64)   NULL,
    operated_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    remark       VARCHAR(255)  NULL,

    PRIMARY KEY (id, operated_at)
) PARTITION BY RANGE (operated_at);

CREATE TABLE sys_operation_log_2026_04 PARTITION OF sys_operation_log
    FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');
CREATE TABLE sys_operation_log_2026_05 PARTITION OF sys_operation_log
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
CREATE TABLE sys_operation_log_2026_06 PARTITION OF sys_operation_log
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');

COMMENT ON TABLE  sys_operation_log           IS '系统操作审计日志，按月分区，永久保留，禁止删除';
COMMENT ON COLUMN sys_operation_log.operation IS '操作类型：SCENE_CREATE/SCENE_ENABLE/SCENE_DISABLE/MODEL_DEPLOY/MODEL_APPROVE/SANDBOX_PROMOTE/DATA_SYNC';

CREATE INDEX idx_oplog_operator   ON sys_operation_log (operator, operated_at DESC);
CREATE INDEX idx_oplog_target     ON sys_operation_log (target_type, target_id, operated_at DESC);

-- ============================================================
-- 3.16 安全网闸数据传输审计表 data_sync_audit（按月分区）
-- ============================================================
CREATE TABLE data_sync_audit (
    id                BIGSERIAL     NOT NULL,
    sync_batch_id     VARCHAR(64)   NOT NULL,
    scene_id          VARCHAR(64)   NOT NULL,
    factory_code      VARCHAR(32)   NOT NULL,
    image_count       INTEGER       NOT NULL,
    desensitize_rule  VARCHAR(128)  NOT NULL,
    total_size_mb     DECIMAL(10,2) NOT NULL,
    source_minio_prefix VARCHAR(255) NOT NULL,
    dest_minio_prefix VARCHAR(255)  NOT NULL,
    gap_device_id     VARCHAR(64)   NOT NULL,
    sync_status       VARCHAR(16)   NOT NULL DEFAULT 'SUCCESS',
    error_msg         TEXT          NULL,
    checksum_before   VARCHAR(64)   NULL,
    checksum_after    VARCHAR(64)   NULL,
    operator          VARCHAR(64)   NOT NULL DEFAULT 'SYSTEM',
    synced_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    PRIMARY KEY (id, synced_at),
    CONSTRAINT ck_sync_status CHECK (sync_status IN ('SUCCESS','PARTIAL','FAILED'))
) PARTITION BY RANGE (synced_at);

CREATE TABLE data_sync_audit_2026_04 PARTITION OF data_sync_audit
    FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');
CREATE TABLE data_sync_audit_2026_05 PARTITION OF data_sync_audit
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
CREATE TABLE data_sync_audit_2026_06 PARTITION OF data_sync_audit
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');

COMMENT ON TABLE  data_sync_audit                    IS '跨安全网闸数据同步审计日志，满足网络安全等级保护合规要求，永久保留不可删除';
COMMENT ON COLUMN data_sync_audit.desensitize_rule   IS '脱敏规则逗号分隔：REMOVE_WATERMARK=去水印，MASK_TIMESTAMPS=时间戳精确到小时，BLUR_METADATA=模糊摄像头标识';
COMMENT ON COLUMN data_sync_audit.gap_device_id      IS '物理单向光闸设备ID，与光闸硬件自身的传输日志交叉核验，实现双重审计';
COMMENT ON COLUMN data_sync_audit.checksum_before    IS '传输前文件列表的 SHA-256 摘要，传输后与 checksum_after 对比，不一致则记录为 PARTIAL 并告警';

CREATE INDEX idx_sync_batch ON data_sync_audit (sync_batch_id, synced_at DESC);

-- ============================================================
-- 3.17 低代码离线仿真任务表 simulation_task
-- ============================================================
CREATE TABLE simulation_task (
    id               BIGSERIAL     PRIMARY KEY,
    task_id          VARCHAR(64)   NOT NULL,
    scene_id         VARCHAR(64)   NOT NULL,
    task_name        VARCHAR(128)  NULL,
    video_file_url   VARCHAR(512)  NOT NULL,
    video_duration_s INTEGER       NULL,
    video_fps        SMALLINT      NULL,
    workflow_json    JSONB         NOT NULL,
    algo_config_json JSONB         NOT NULL DEFAULT '{}',
    status           VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    total_frames     INTEGER       NOT NULL DEFAULT 0,
    matched_alarms   INTEGER       NOT NULL DEFAULT 0,
    false_alarm_count INTEGER      NOT NULL DEFAULT 0,
    result_summary   VARCHAR(512)  NULL,
    result_json      JSONB         NULL,
    error_msg        TEXT          NULL,
    started_at       TIMESTAMPTZ   NULL,
    finished_at      TIMESTAMPTZ   NULL,
    is_deleted       BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(64)   NOT NULL,
    updated_by       VARCHAR(64)   NOT NULL,
    version          INTEGER       NOT NULL DEFAULT 1,

    CONSTRAINT ck_sim_status CHECK (status IN ('PENDING','RUNNING','COMPLETED','FAILED'))
);

CREATE UNIQUE INDEX uq_simulation_task_id_active
    ON simulation_task (task_id)
    WHERE is_deleted = FALSE;

CREATE INDEX idx_sim_scene_created ON simulation_task (scene_id, created_at DESC)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE  simulation_task                  IS '低代码编排器离线视频仿真调试任务表，与实时Sandbox互补，构成完整调试闭环';
COMMENT ON COLUMN simulation_task.matched_alarms   IS '仿真执行中触发告警的次数。注意：仿真告警不推送到任何外部系统';
COMMENT ON COLUMN simulation_task.result_json      IS '各节点执行结果详情，结构：{nodes:[{node_id, type, trigger_count, avg_ms, error_count}]}';

CREATE TRIGGER trg_simulation_task_updated_at
    BEFORE UPDATE ON simulation_task
    FOR EACH ROW EXECUTE FUNCTION fn_update_updated_at();

-- ============================================================
-- 3.18 录像回放会话表 replay_session（★V2.1 新增）
-- ============================================================
CREATE TABLE replay_session (
    id               BIGSERIAL     PRIMARY KEY,
    session_id       VARCHAR(64)   NOT NULL,
    scene_id         VARCHAR(64)   NOT NULL,
    minio_video_url  VARCHAR(512)  NOT NULL,
    replay_fps       SMALLINT      NOT NULL DEFAULT 5,
    loop_count       SMALLINT      NOT NULL DEFAULT 1,
    status           VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    total_frames     INTEGER       NOT NULL DEFAULT 0,
    sent_frames      INTEGER       NOT NULL DEFAULT 0,
    error_message    TEXT          NULL,
    started_at       TIMESTAMPTZ   NULL,
    finished_at      TIMESTAMPTZ   NULL,
    is_deleted       BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    created_by       VARCHAR(64)   NOT NULL,
    updated_by       VARCHAR(64)   NOT NULL,
    version_lock     INTEGER       NOT NULL DEFAULT 1,

    CONSTRAINT ck_replay_status  CHECK (status IN ('PENDING','RUNNING','COMPLETED','STOPPED','FAILED')),
    CONSTRAINT ck_replay_fps     CHECK (replay_fps BETWEEN 1 AND 30),
    CONSTRAINT ck_loop_count     CHECK (loop_count >= 0),
    CONSTRAINT fk_replay_scene   FOREIGN KEY (scene_id) REFERENCES scene_config(scene_id)
);

-- 会话ID部分索引（软删除友好）
CREATE UNIQUE INDEX uq_replay_session_id_active
    ON replay_session (session_id)
    WHERE is_deleted = FALSE;

-- 同一场景最多 1 个 RUNNING 会话（防止重复回放）
CREATE UNIQUE INDEX uq_replay_scene_running
    ON replay_session (scene_id)
    WHERE status = 'RUNNING' AND is_deleted = FALSE;

-- 历史查询：按场景和创建时间倒序
CREATE INDEX idx_replay_scene_created
    ON replay_session (scene_id, created_at DESC)
    WHERE is_deleted = FALSE;

COMMENT ON TABLE  replay_session                  IS '录像回放服务会话表（V2.1），实验室阶段替代摄像头，支撑全流水线无硬件验证';
COMMENT ON COLUMN replay_session.session_id       IS '会话唯一ID，格式：REPLAY-SCENE-SINTER-FIRE-001-20260415143000';
COMMENT ON COLUMN replay_session.minio_video_url  IS '待回放的视频 MinIO 路径，格式：minio://tianjing-frames-prod/sintering/lab-recordings/xxx.mp4';
COMMENT ON COLUMN replay_session.replay_fps       IS '回放帧率。实验室验证默认 5fps（节省云端 API 调用次数）';
COMMENT ON COLUMN replay_session.loop_count       IS '循环播放次数。0=无限循环，1=播完即停，>1=指定次数';
COMMENT ON COLUMN replay_session.sent_frames      IS '已成功投递到 Kafka tianjing.frames.prod 的帧数，可计算进度';

-- 注意：replay_session 使用 version_lock 而非 version
CREATE OR REPLACE FUNCTION fn_replay_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at   = NOW();
    NEW.version_lock = OLD.version_lock + 1;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_replay_session_updated_at
    BEFORE UPDATE ON replay_session
    FOR EACH ROW EXECUTE FUNCTION fn_replay_updated_at();
