package com.tianzhu.tianjing.dashboard.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * 推理趋势数据点 DTO
 * 规范：API 接口规范 V3.1 §6.13
 * 序列化为 snake_case（前端接口约定）
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TrendPointDTO(
        String time,
        Long count,
        Long alarms,
        Double avgLatencyMs
) {
    public TrendPointDTO {
        count = count != null ? count : 0L;
        alarms = alarms != null ? alarms : 0L;
        avgLatencyMs = avgLatencyMs != null ? avgLatencyMs : 0.0;
    }
}
