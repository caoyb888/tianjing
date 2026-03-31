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
    private String batchId;
    /** 同步类型：PROD_TO_TRAIN / MODEL_TO_STAGING */
    private String syncType;
    private Integer fileCount;
    private Long totalBytes;
    private String checksumMethod;
    private String status;
    private String errorMessage;
    @TableField(fill = FieldFill.INSERT) private OffsetDateTime createdAt;
    private OffsetDateTime completedAt;
    private String createdBy;
}
