package com.tianzhu.tianjing.alarm.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 告警详情 DTO（告警详情页 AlarmDetailView 所需字段）
 *
 * 规范：CLAUDE.md §15 P1-10 — Controller 禁止直接返回 @TableName 实体
 * 字段对齐前端 types/index.ts AlarmRecord 接口
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AlarmDetailDTO(
        String alarmId,
        String sceneId,
        /** 对应前端 factory 字段，来自实体 factoryCode */
        String factory,
        String alarmLevel,
        String anomalyType,
        double confidence,
        /** HTTP 可访问路径，minio:// 转换为 /minio-frames/ */
        String imageUrl,
        /** 解析自 bboxJson，分类插件为空列表 */
        List<DetectionDTO> detections,
        boolean isSandbox,
        String pushStatus,
        /** TRUE_POSITIVE / FALSE_POSITIVE / FALSE_NEGATIVE / null（未处置） */
        String feedbackStatus,
        /** 对应前端 timestamp 字段，来自实体 alarmAt（ISO-8601） */
        String timestamp,
        String feedbackAt,
        String pluginId
) {
    /** 检测框 DTO（对应前端 AlarmDetection 接口） */
    public record DetectionDTO(
            int class_id,
            String class_name,
            double confidence,
            BBoxDTO bbox
    ) {}

    public record BBoxDTO(float x1, float y1, float x2, float y2) {}
}
