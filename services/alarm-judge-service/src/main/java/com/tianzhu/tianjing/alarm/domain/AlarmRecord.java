package com.tianzhu.tianjing.alarm.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 告警记录实体（对应 alarm_record 表，按月分区）
 *
 * SECURITY: is_sandbox 字段是核心安全标记
 * is_sandbox=true 的告警绝不允许触发外部推送（P0 红线）
 */
@Data
@TableName("alarm_record")
public class AlarmRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("alarm_id")
    private String alarmId;

    @TableField("scene_id")
    private String sceneId;

    @TableField("factory_code")
    private String factoryCode;

    /** 告警级别：CRITICAL / WARNING / INFO / SANDBOX_CRITICAL / SANDBOX_WARNING */
    @TableField("alarm_level")
    private String alarmLevel;

    @TableField("anomaly_type")
    private String anomalyType;

    @TableField("confidence")
    private Double confidence;

    @TableField("bbox_json")
    private String bboxJson;

    @TableField("measurement_val")
    private Double measurementVal;

    @TableField("measurement_unit")
    private String measurementUnit;

    @TableField("image_url")
    private String imageUrl;

    @TableField("frame_id")
    private String frameId;

    @TableField("plugin_id")
    private String pluginId;

    @TableField("model_version_id")
    private String modelVersionId;

    /** DB列名 confirm_frames（非confirmed_frames） */
    @TableField("confirm_frames")
    private Integer confirmFrames;

    /**
     * SECURITY: Sandbox 标识
     * 此字段为 true 时，alarm-judge-service 必须拦截，禁止任何外部推送
     * 规范：CLAUDE.md §11.1，P0 安全红线
     */
    @TableField("is_sandbox")
    private Boolean isSandbox;

    /** 推送状态：PENDING / SUCCESS / FAILED / INTERCEPTED（Sandbox 拦截） */
    @TableField("push_status")
    private String pushStatus;

    @TableField("push_channels_json")
    private String pushChannelsJson;

    @TableField("alarm_at")
    private OffsetDateTime alarmAt;

    @TableField("resolved_at")
    private OffsetDateTime resolvedAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
