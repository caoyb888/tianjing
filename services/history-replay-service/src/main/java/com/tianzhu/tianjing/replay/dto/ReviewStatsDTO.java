package com.tianzhu.tianjing.replay.dto;

/**
 * 审核进度统计 DTO
 */
public record ReviewStatsDTO(
        int totalFrames,      // 总帧数
        int reviewedFrames,   // 已审核帧数（APPROVED + REJECTED）
        int pendingFrames,    // 待审帧数
        int approvedFrames,   // 已通过帧数
        int rejectedFrames,   // 已拒绝帧数
        int skippedFrames,    // 已跳过帧数
        int progressPercent   // 审核进度百分比（0-100）
) {}
