package com.tianzhu.tianjing.replay.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 帧详情 DTO（含原始标注 + 校正标注）
 */
public record FrameDetailDTO(
        String frameId,                   // 帧编号
        String frameUrl,                  // MinIO 图像地址
        Integer frameIndex,               // 帧序号
        String reviewStatus,              // 审核状态
        Boolean isModified,               // 是否修改过
        String reviewedBy,                // 审核人
        OffsetDateTime reviewedAt,        // 审核时间
        List<DetectionDTO> originalDetections,   // 原始检测框
        List<DetectionDTO> correctedDetections   // 校正后检测框（null 表示未修改）
) {}
