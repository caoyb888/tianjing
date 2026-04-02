package com.tianzhu.tianjing.drift.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 模型漂移精度指标（对应 drift_metric 表）
 * 每日凌晨计算一次，连续 3 天 Precision < 85% 触发重训练
 */
@Data
@TableName("drift_metric")
public class DriftMetric {
    @TableId(type = IdType.AUTO) private Long id;

    @TableField("metric_id")
    private String metricId;

    @TableField("scene_id")
    private String sceneId;

    @TableField("model_version_id")
    private String modelVersionId;

    @TableField("metric_date")
    private LocalDate metricDate;

    /** DB列名 total_alarms */
    @TableField("total_alarms")
    private Integer totalAlarms;

    /** DB列名 tp_count */
    @TableField("tp_count")
    private Integer tpCount;

    /** DB列名 fp_count */
    @TableField("fp_count")
    private Integer fpCount;

    /** DB列名 fn_count */
    @TableField("fn_count")
    private Integer fnCount;

    /** DB列名 precision_val（precision 是 SQL 保留字） */
    @TableField("precision_val")
    private Double precisionVal;

    /** DB列名 recall_val */
    @TableField("recall_val")
    private Double recallVal;

    @TableField("f1_score")
    private Double f1Score;

    /** 人工反馈覆盖率（0-1） */
    @TableField("feedback_coverage")
    private Double feedbackCoverage;

    @TableField("is_below_threshold")
    private Boolean isBelowThreshold;

    /** 是否已触发重训练（布尔标记） */
    @TableField("retrain_triggered")
    private Boolean retrainTriggered;

    /** 关联的重训练作业 ID */
    @TableField("retrain_job_id")
    private String retrainJobId;

    /** 连续低于阈值的天数 */
    @TableField("consecutive_below")
    private Integer consecutiveBelow;

    @TableField(value = "created_at", fill = FieldFill.INSERT) private OffsetDateTime createdAt;

    @TableField("created_by")
    private String createdBy;
}
