package com.tianzhu.tianjing.alarm.dto;

import java.time.OffsetDateTime;

public record AlarmQueryParams(
        int page,
        int size,
        String sceneId,
        String alarmLevel,
        String factoryCode,
        Boolean isSandbox,
        OffsetDateTime startTime,
        OffsetDateTime endTime
) {}
