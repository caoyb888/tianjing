package com.tianzhu.tianjing.dashboard.dto;

/**
 * 厂部汇总统计 DTO（用于大屏热力柱图）
 * 规范：实时告警大屏开发优化计划.md §4.3
 */
public record FactorySummaryDTO(
        String factory,
        String factoryName,
        Integer todayAlarms,
        Integer activeScenes,
        Integer onlineDevices,
        Long todayInferences
) {
    public FactorySummaryDTO {
        todayAlarms = todayAlarms != null ? todayAlarms : 0;
        activeScenes = activeScenes != null ? activeScenes : 0;
        onlineDevices = onlineDevices != null ? onlineDevices : 0;
        todayInferences = todayInferences != null ? todayInferences : 0L;
    }
}
