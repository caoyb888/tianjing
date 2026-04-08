package com.tianzhu.tianjing.replay.dto;

/**
 * 边界框 DTO
 */
public record BBoxDTO(
        double x1,  // 左上角 x
        double y1,  // 左上角 y
        double x2,  // 右下角 x
        double y2   // 右下角 y
) {}
