package com.tianzhu.tianjing.calibration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CalibrationRequest(
        @NotBlank @JsonProperty("device_code") String deviceCode,

        @NotNull @Positive @JsonProperty("ref_length_mm")
        BigDecimal refLengthMm,

        @NotNull @JsonProperty("pt1_x") Integer pt1X,
        @NotNull @JsonProperty("pt1_y") Integer pt1Y,
        @NotNull @JsonProperty("pt2_x") Integer pt2X,
        @NotNull @JsonProperty("pt2_y") Integer pt2Y,

        @JsonProperty("frame_image_url") String frameImageUrl
) {}
