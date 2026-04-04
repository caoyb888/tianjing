package com.tianzhu.tianjing.algomodel.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

/**
 * 模型版本实体（对应 model_version 表）
 *
 * 状态机：STAGING → SANDBOX_VALIDATING → REVIEWING → PRODUCTION → DEPRECATED
 * 四眼原则：approve 审核人 ≠ 提交人（返回 4004）
 */
@Data
@TableName("model_version")
public class ModelVersion {
    @TableId(type = IdType.AUTO) private Long id;

    /** DB列名 version_id */
    @TableField("version_id")
    private String versionId;

    @TableField("plugin_id")
    private String pluginId;

    /** DB列名 train_job_id */
    @TableField("train_job_id")
    private String trainJobId;

    @TableField("mlflow_run_id")
    private String mlflowRunId;

    @TableField("mlflow_model_uri")
    private String mlflowModelUri;

    /** DB列名 model_path */
    @TableField("model_path")
    private String modelPath;

    @TableField("export_format")
    private String exportFormat;

    @TableField("map50")
    private Double map50;

    /** DB列名 map50_95 */
    @TableField("map50_95")
    private Double map5095;

    @TableField("precision_score")
    private Double precisionScore;

    @TableField("recall_score")
    private Double recallScore;

    /** DB列名 inference_ms_gpu */
    @TableField("inference_ms_gpu")
    private Double inferenceMs_gpu;

    /** DB列名 inference_ms_cpu */
    @TableField("inference_ms_cpu")
    private Double inferenceMs_cpu;

    @TableField("model_size_mb")
    private Double modelSizeMb;

    /** 状态机：STAGING / SANDBOX_VALIDATING / REVIEWING / PRODUCTION / DEPRECATED */
    @TableField("status")
    private String status;

    @TableField("sandbox_hours")
    private Integer sandboxHours;

    @TableField("sandbox_precision")
    private Double sandboxPrecision;

    @TableField("sandbox_recall")
    private Double sandboxRecall;

    /** 提交审核人，DB列名 submitted_by */
    @TableField("submitted_by")
    private String submittedBy;

    @TableField("approved_by")
    private String approvedBy;

    @TableField("approved_at")
    private OffsetDateTime approvedAt;

    @TableField("deployed_at")
    private OffsetDateTime deployedAt;

    @TableField("deprecated_at")
    private OffsetDateTime deprecatedAt;

    @TableLogic(value = "false", delval = "true")
    private Boolean isDeleted;

    @TableField(value = "created_at", fill = FieldFill.INSERT) private OffsetDateTime createdAt;
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE) private OffsetDateTime updatedAt;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;

    @Version
    private Integer version;
}
