package com.tianzhu.tianjing.dashboard.dto;

/**
 * 推理趋势数据点 DTO
 * 规范：API 接口规范 V3.1 §6.13
 */
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
