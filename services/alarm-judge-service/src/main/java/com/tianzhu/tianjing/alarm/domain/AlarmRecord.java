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

    private String alarmId;
    private String sceneId;
    private String factoryCode;

    /** 告警级别：CRITICAL / WARNING / INFO / SANDBOX_CRITICAL / SANDBOX_WARNING */
    private String alarmLevel;

    private String anomalyType;
    private Double confidence;
    private Integer confirmedFrames;

    /** 告警图像 MinIO 路径 */
    private String imageUrl;

    /** 推理结果快照（JSON）*/
    private String inferResultJson;

    /**
     * SECURITY: Sandbox 标识
     * 此字段为 true 时，alarm-judge-service 必须拦截，禁止任何外部推送
     * 规范：CLAUDE.md §11.1，P0 安全红线
     */
    private Boolean isSandbox;

    /** 推送状态：PENDING / SUCCESS / FAILED / INTERCEPTED（Sandbox 拦截） */
    private String pushStatus;

    /** 关联告警规则 ID */
    private String alarmRuleId;

    private OffsetDateTime alarmAt;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
