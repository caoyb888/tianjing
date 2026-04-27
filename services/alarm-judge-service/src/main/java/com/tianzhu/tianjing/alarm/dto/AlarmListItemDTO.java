package com.tianzhu.tianjing.alarm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 告警列表条目 DTO（告警列表页 AlarmListView 所需字段）
 *
 * 规范：CLAUDE.md §15 P1-10 — Controller 禁止直接返回 @TableName 实体
 * 列表页不需要检测框，保持轻量
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AlarmListItemDTO(
        String alarmId,
        String sceneId,
        String factory,
        String alarmLevel,
        String anomalyType,
        double confidence,
        String imageUrl,
        boolean isSandbox,
        String pushStatus,
        String feedbackStatus,
        String timestamp
) {}
