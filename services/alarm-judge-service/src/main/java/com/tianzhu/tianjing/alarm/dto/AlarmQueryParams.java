package com.tianzhu.tianjing.alarm.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 告警查询参数
 * 规范：API 接口规范 V3.1 §6.7
 * 扩展：实时告警大屏开发优化计划.md Phase 1（新增 levels 多选过滤）
 */
public record AlarmQueryParams(
        int page,
        int size,
        String sceneId,
        String alarmLevel,
        String factoryCode,
        Boolean isSandbox,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        List<String> levels  // 新增：告警级别多选（CRITICAL/WARNING/INFO）
) {
    public AlarmQueryParams {
        levels = levels != null ? levels : List.of();
    }

    /**
     * 兼容原有构造方式（无 levels 参数）
     */
    public AlarmQueryParams(int page, int size, String sceneId, String alarmLevel,
                            String factoryCode, Boolean isSandbox,
                            OffsetDateTime startTime, OffsetDateTime endTime) {
        this(page, size, sceneId, alarmLevel, factoryCode, isSandbox, startTime, endTime, List.of());
    }
}
