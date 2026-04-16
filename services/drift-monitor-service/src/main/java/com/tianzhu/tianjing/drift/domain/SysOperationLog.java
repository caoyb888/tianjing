package com.tianzhu.tianjing.drift.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

/**
 * 操作审计日志（对应 sys_operation_log 表）
 */
@Data
@TableName("sys_operation_log")
public class SysOperationLog {
    @TableId(type = IdType.AUTO) private Long id;

    @TableField("log_id")
    private String logId;

    /** DB列名 operator */
    @TableField("operator")
    private String operator;

    /** DB列名 operation */
    @TableField("operation")
    private String operation;

    /** DB列名 target_type */
    @TableField("target_type")
    private String targetType;

    /** DB列名 target_id */
    @TableField("target_id")
    private String targetId;

    @TableField("before_json")
    private String beforeJson;

    @TableField("after_json")
    private String afterJson;

    /** DB列名 ip_address */
    @TableField("ip_address")
    private String ipAddress;

    @TableField("trace_id")
    private String traceId;

    @TableField("remark")
    private String remark;

    /** DB列名 operated_at（非 created_at） */
    @TableField("operated_at")
    private OffsetDateTime operatedAt;
}
