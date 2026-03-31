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
    private String modelVersionId;
    private String pluginId;
    private String version;
    private String trainingJobId;
    private String mlflowRunId;
    private String modelArtifactUrl;
    private String map50;
    private String map5095;
    private Double inferMsGpu;
    private Double inferMsCpu;
    /** 状态机：STAGING / SANDBOX_VALIDATING / REVIEWING / PRODUCTION / DEPRECATED */
    private String status;
    private String submittedBy;
    private String reviewedBy;
    private String reviewComment;
    private OffsetDateTime reviewedAt;
    @TableField(fill = FieldFill.INSERT) private OffsetDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE) private OffsetDateTime updatedAt;
    private String createdBy;
}
