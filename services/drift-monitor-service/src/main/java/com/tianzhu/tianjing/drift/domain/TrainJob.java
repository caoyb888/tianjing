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

    @TableField("job_id")
    private String jobId;

    @TableField("plugin_id")
    private String pluginId;

    @TableField("dataset_version_id")
    private String datasetVersionId;

    /** 漂移触发时关联的 drift_metric_id */
    @TableField("drift_metric_id")
    private String driftMetricId;

    @TableField("train_config_json")
    private String trainConfigJson;

    @TableField("gpu_count")
    private Integer gpuCount;

    /** 触发方式：MANUAL / AUTO_DRIFT / SCHEDULED */
    @TableField("trigger_type")
    private String triggerType;

    /** 状态：PENDING / RUNNING / COMPLETED / FAILED / CANCELLED */
    @TableField("status")
    private String status;

    @TableField("best_epoch")
    private Integer bestEpoch;

    @TableField("best_map50")
    private Double bestMap50;

    /** DB列名 best_map50_95（注意：非 best_map5095） */
    @TableField("best_map50_95")
    private Double bestMap5095;

    @TableField("mlflow_run_id")
    private String mlflowRunId;

    @TableField("error_msg")
    private String errorMsg;

    @TableField("started_at")
    private OffsetDateTime startedAt;

    @TableField("finished_at")
    private OffsetDateTime finishedAt;

    @TableField(value = "created_at", fill = FieldFill.INSERT) private OffsetDateTime createdAt;

    @TableField("created_by")
    private String createdBy;
}
