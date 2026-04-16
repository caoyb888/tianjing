package com.tianzhu.tianjing.alarm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 告警人工复核反馈请求体
 * 规范：S2-09 要求三选项（TRUE_POSITIVE / FALSE_POSITIVE / FALSE_NEGATIVE）
 *
 * feedback_type 取值说明：
 *   TRUE_POSITIVE  — 确认告警（模型判断正确，现场确有异常）
 *   FALSE_POSITIVE — 误报（模型误判，现场无异常）
 *   FALSE_NEGATIVE — 漏报（现场有异常但模型未检出，补录此次漏检）
 *
 * 下游：tianjing.drift.feedback Topic 消费方（drift-monitor-service）
 * 依据三分类计算 Precision / Recall，驱动自动重训练门禁
 */
public record AlarmFeedbackRequest(
        @NotBlank
        @Pattern(regexp = "TRUE_POSITIVE|FALSE_POSITIVE|FALSE_NEGATIVE",
                 message = "feedback_type 必须为 TRUE_POSITIVE / FALSE_POSITIVE / FALSE_NEGATIVE")
        @JsonProperty("feedback_type") String feedbackType,
        String comment
) {}
