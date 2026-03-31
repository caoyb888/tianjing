package com.tianzhu.tianjing.alarm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record AlarmFeedbackRequest(
        @NotNull @JsonProperty("is_false_positive") Boolean isFalsePositive,
        String comment
) {}
