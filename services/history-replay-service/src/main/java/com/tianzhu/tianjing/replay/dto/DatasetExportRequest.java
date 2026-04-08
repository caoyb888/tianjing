package com.tianzhu.tianjing.replay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

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
        Boolean includeNegatives,

        /**
         * 导出模式：ALL（全部帧）/ REVIEWED_ONLY（仅已审核通过帧）
         * 默认 "ALL"（向后兼容）
         */
        @Pattern(regexp = "ALL|REVIEWED_ONLY", message = "export_mode 必须是 ALL 或 REVIEWED_ONLY")
        @JsonProperty("export_mode")
        String exportMode
) {
    public double effectiveConfThreshold() {
        return confThreshold != null ? confThreshold : 0.70;
    }

    public boolean effectiveIncludeNegatives() {
        return includeNegatives == null || includeNegatives;
    }

    /**
     * 获取有效的导出模式，默认 ALL（向后兼容）
     */
    public String effectiveExportMode() {
        return exportMode != null ? exportMode : "ALL";
    }

    /**
     * 是否仅导出已审核通过的帧
     */
    public boolean isReviewedOnly() {
        return "REVIEWED_ONLY".equals(effectiveExportMode());
    }
}
