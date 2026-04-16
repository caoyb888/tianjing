package com.tianzhu.tianjing.replay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * 保存帧审核结果请求
 */
public record SaveFrameRequest(
        @NotBlank
        @Pattern(regexp = "APPROVED|REJECTED|SKIPPED", message = "审核状态必须是 APPROVED/REJECTED/SKIPPED")
        @JsonProperty("review_status")
        String reviewStatus,

        /**
         * 校正后的检测框列表
         * - null 表示未修改，使用原始结果
         * - [] 空数组表示明确标注为无目标负样本
         * - 有内容表示使用校正后的框
         */
        @JsonProperty("corrected_detections")
        List<DetectionDTO> correctedDetections
) {}
