package com.tianzhu.tianjing.drift.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

/**
 * 训练作业（对应 train_job 表，存储于 tianjing_train 库）
 * 规范：API 接口规范 V3.1 §6.10
 */
@Data
@TableName("train_job")
public class TrainJob {
    @TableId(type = IdType.AUTO) private Long id;
    private String jobId;
    private String pluginId;
    private String datasetVersionId;
    private String trainConfigJson;
    private Integer gpuCount;
    /** 触发方式：MANUAL / AUTO_DRIFT / SCHEDULED */
    private String triggerType;
    private String triggerReason;
    /** 状态：PENDING / RUNNING / COMPLETED / FAILED / CANCELLED */
    private String status;
    private Integer bestEpoch;
    private Double bestMap50;
    private String mlflowRunId;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
    private OffsetDateTime estimatedStartAt;
    @TableField(fill = FieldFill.INSERT) private OffsetDateTime createdAt;
    private String createdBy;
}
