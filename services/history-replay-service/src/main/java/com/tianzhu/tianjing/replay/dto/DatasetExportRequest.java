package com.tianzhu.tianjing.replay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

/**
 * 导出训练数据集请求
 * 对应 POST /simulations/{task_id}/export-dataset
 */
public record DatasetExportRequest(

        /** 目标数据集版本 ID，不存在时自动在 tianjing_train.dataset_version 创建 */
        @NotBlank(message = "数据集版本 ID 不能为空")
        @JsonProperty("dataset_version_id")
        String datasetVersionId,

        /** 所属数据集编号（新建版本时使用） */
        @JsonProperty("dataset_code")
        String datasetCode,

        /** 置信度过滤阈值，低于此值的检测框不纳入标注，默认 0.70 */
        @DecimalMin("0.0") @DecimalMax("1.0")
        @JsonProperty("conf_threshold")
        Double confThreshold,

        /** 是否包含负样本帧（无检测结果的帧），默认 true */
        @JsonProperty("include_negatives")
        Boolean includeNegatives
) {
    public double effectiveConfThreshold() {
        return confThreshold != null ? confThreshold : 0.70;
    }

    public boolean effectiveIncludeNegatives() {
        return includeNegatives == null || includeNegatives;
    }
}
