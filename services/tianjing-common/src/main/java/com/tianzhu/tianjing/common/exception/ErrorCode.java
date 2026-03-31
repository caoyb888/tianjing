package com.tianzhu.tianjing.common.exception;

import lombok.Getter;

/**
 * 完整错误码枚举
 * 规范：API 接口规范 V3.1 §4.2
 * HTTP 状态码与错误码映射见 §4.1
 */
@Getter
public enum ErrorCode {

    // ==============================
    // 1xxx — 参数错误（HTTP 400）
    // ==============================
    PARAM_MISSING(1001, "请求参数缺失或格式错误"),
    PARAM_JSON_INVALID(1002, "JSON 格式错误，无法解析请求体"),
    PARAM_PAGE_OUT_OF_RANGE(1003, "分页参数超出范围"),
    PARAM_TIME_RANGE_INVALID(1004, "时间范围参数错误，start_time 不得晚于 end_time"),
    PARAM_ENUM_INVALID(1005, "枚举值不在允许范围内"),

    // ==============================
    // 2xxx — 业务逻辑错误（HTTP 400 / 409）
    // ==============================
    OPTIMISTIC_LOCK_CONFLICT(2001, "乐观锁版本冲突，请刷新后重试"),
    RESOURCE_STATE_FORBIDDEN(2002, "资源当前状态不允许此操作"),
    SCENE_ENABLE_PRECONDITION(2003, "场景启用前置条件不满足（未绑定摄像头/模型）"),
    CAMERA_BIND_CONFLICT(2004, "摄像头绑定冲突，场景已绑定摄像头"),
    SANDBOX_HOURS_INSUFFICIENT(2005, "Sandbox 验证时长不足，需要 >= 48 小时"),
    ALARM_NOT_FAILED(2006, "告警推送状态不为 FAILED，无需重试"),
    SANDBOX_GATE_NOT_PASS(2007, "Sandbox 转正门禁未全部通过"),
    ALARM_FEEDBACK_DUPLICATE(2008, "告警已被反馈，不允许重复提交"),
    SIMULATION_VIDEO_EXPIRED(2009, "仿真任务视频文件不存在或已过期（> 72 小时）"),
    DATA_SYNC_CHECKSUM_FAIL(2010, "数据同步批次完整性校验失败"),
    TRAIN_DATASET_NOT_FOUND(2011, "训练作业对应的数据集版本不存在或已被删除"),
    TRAIN_GPU_QUOTA_EXCEEDED(2012, "训练作业所需 GPU 资源超出训练集群配额"),
    LIVE_STREAM_FAILED(2013, "视频流转换失败，RTSP 源不可达或格式不支持"),
    TRAIN_JOB_CANCEL_FORBIDDEN(2014, "训练作业当前状态不允许取消"),
    REPLAY_NOT_RUNNING(2015, "录像回放任务不存在或已结束，无法停止"),
    REPLAY_ALREADY_RUNNING(2016, "同一场景已有录像回放任务在运行，请先停止当前任务"),

    // ==============================
    // 3xxx — 资源不存在（HTTP 404）
    // ==============================
    SCENE_NOT_FOUND(3001, "场景不存在"),
    DEVICE_NOT_FOUND(3002, "摄像头设备不存在"),
    ALGO_PLUGIN_NOT_FOUND(3003, "算法插件不存在"),
    MODEL_VERSION_NOT_FOUND(3004, "模型版本不存在"),
    ALARM_NOT_FOUND(3005, "告警记录不存在"),
    SANDBOX_SESSION_NOT_FOUND(3006, "Sandbox 会话不存在"),
    SIMULATION_TASK_NOT_FOUND(3007, "仿真任务不存在"),
    USER_NOT_FOUND(3008, "用户不存在"),
    CALIBRATION_NOT_FOUND(3009, "标定记录不存在"),
    DATASET_NOT_FOUND(3010, "数据集不存在"),
    TRAIN_JOB_NOT_FOUND(3011, "训练作业不存在"),
    REPLAY_SESSION_NOT_FOUND(3012, "录像回放会话不存在"),

    // ==============================
    // 4xxx — 权限错误（HTTP 401 / 403）
    // ==============================
    TOKEN_MISSING_OR_EXPIRED(4001, "Token 未携带或已过期"),
    CREDENTIALS_INVALID(4002, "用户名或密码错误"),
    PERMISSION_DENIED(4003, "当前角色无权执行此操作"),
    FOUR_EYES_VIOLATION(4004, "违反四眼原则，审核人与提交人不能为同一人"),
    ACCOUNT_LOCKED(4005, "账号已被锁定，请联系管理员"),

    // ==============================
    // 5xxx — 服务端内部错误（HTTP 500）
    // ==============================
    INTERNAL_ERROR(5001, "服务内部错误"),
    DB_CONNECTION_FAILED(5002, "数据库连接失败或超时"),
    REDIS_ERROR(5003, "Redis 缓存异常"),
    KAFKA_SEND_FAILED(5004, "Kafka 消息发送失败"),
    MINIO_ACCESS_FAILED(5005, "MinIO 存储访问失败"),
    INFERENCE_SERVICE_UNAVAILABLE(5006, "推理服务不可用"),
    MQTT_PUSH_FAILED(5007, "MQTT 推送失败，工业互联网平台不可达"),
    GRPC_TIMEOUT(5008, "依赖服务超时");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
