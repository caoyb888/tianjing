package com.tianzhu.tianjing.dashboard.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.time.OffsetDateTime;

/**
 * 平台概览统计数据 DTO
 * 规范：实时告警大屏开发优化计划.md §3.2
 * 序列化为 snake_case（前端接口约定）
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OverviewStatsDTO(
        // 统计卡片
        Integer activeScenes,
        Integer onlineDevices,
        Integer todayAlarms,
        Long todayInferences,

        // 告警级别分布（饼图）
        Integer criticalAlarms,
        Integer warningAlarms,
        Integer infoAlarms,

        // 附加信息
        Integer totalScenes,
        Double avgInferLatencyMs,
        Integer sandboxSessionsRunning,
        OffsetDateTime generatedAt
) {
    public OverviewStatsDTO {
        activeScenes = activeScenes != null ? activeScenes : 0;
        onlineDevices = onlineDevices != null ? onlineDevices : 0;
        todayAlarms = todayAlarms != null ? todayAlarms : 0;
        todayInferences = todayInferences != null ? todayInferences : 0L;
        criticalAlarms = criticalAlarms != null ? criticalAlarms : 0;
        warningAlarms = warningAlarms != null ? warningAlarms : 0;
        infoAlarms = infoAlarms != null ? infoAlarms : 0;
        totalScenes = totalScenes != null ? totalScenes : 0;
        avgInferLatencyMs = avgInferLatencyMs != null ? avgInferLatencyMs : 0.0;
        sandboxSessionsRunning = sandboxSessionsRunning != null ? sandboxSessionsRunning : 0;
        generatedAt = generatedAt != null ? generatedAt : OffsetDateTime.now();
    }
}
