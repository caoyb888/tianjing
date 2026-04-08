package com.tianzhu.tianjing.replay.dto;

/**
 * 检测框 DTO（用于标注审核）
 * 与 result_json 中的 detections 结构保持一致
 */
public record DetectionDTO(
        Integer classId,    // 类别ID
        String className,   // 类别名称
        double confidence,  // 置信度（人工新增的框固定为 1.0）
        BBoxDTO bbox        // 边界框坐标
) {}
