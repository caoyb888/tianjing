package com.tianzhu.tianjing.replay.dto;

/**
 * 帧列表项 DTO（用于缩略图导航）
 */
public record FrameListItemDTO(
        String frameId,         // 帧编号
        String frameUrl,        // MinIO 图像地址
        Integer frameIndex,     // 帧序号
        String reviewStatus,    // PENDING/APPROVED/REJECTED/SKIPPED
        Boolean isModified,     // 是否人工修改过
        Integer detectionCount, // 原始检测框数量
        Integer correctedCount  // 校正后数量（未审核时 null）
) {}
