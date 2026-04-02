package com.tianzhu.tianjing.drift.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

/**
 * 数据同步审计日志（对应 data_sync_audit 表）
 * 规范：API 接口规范 V3.1 §6.12
 * V2.0：光闸未到位，同步改为 MinIO bucket 间文件复制 + checksum 验证
 */
@Data
@TableName("data_sync_audit")
public class DataSyncAudit {
    @TableId(type = IdType.AUTO) private Long id;

    /** DB列名 sync_batch_id */
    @TableField("sync_batch_id")
    private String syncBatchId;

    @TableField("scene_id")
    private String sceneId;

    @TableField("factory_code")
    private String factoryCode;

    /** DB列名 image_count */
    @TableField("image_count")
    private Integer imageCount;

    @TableField("desensitize_rule")
    private String desensitizeRule;

    /** DB列名 total_size_mb */
    @TableField("total_size_mb")
    private Double totalSizeMb;

    @TableField("source_minio_prefix")
    private String sourceMinioPprefix;

    @TableField("dest_minio_prefix")
    private String destMinioPrefix;

    @TableField("gap_device_id")
    private String gapDeviceId;

    /** DB列名 sync_status */
    @TableField("sync_status")
    private String syncStatus;

    /** DB列名 error_msg */
    @TableField("error_msg")
    private String errorMsg;

    @TableField("checksum_before")
    private String checksumBefore;

    @TableField("checksum_after")
    private String checksumAfter;

    @TableField("operator")
    private String operator;

    /** DB列名 synced_at */
    @TableField("synced_at")
    private OffsetDateTime syncedAt;

    /** data_sync_audit 表无 created_at 列（分区键为 synced_at） */
    @TableField(exist = false)
    private OffsetDateTime createdAt;
}
