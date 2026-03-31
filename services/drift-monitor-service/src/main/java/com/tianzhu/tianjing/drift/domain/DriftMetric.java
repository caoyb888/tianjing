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
    private String metricId;
    private String sceneId;
    private LocalDate metricDate;
    private Integer totalInferCount;
    private Integer humanFeedbackCount;
    private Integer truePositives;
    private Integer falsePositives;
    private Integer falseNegatives;
    private Double precision;
    private Double recall;
    /** 触发重训练的指标 ID（不为空则已触发） */
    private String retrainTriggeredBy;
    @TableField(fill = FieldFill.INSERT) private OffsetDateTime createdAt;
}
