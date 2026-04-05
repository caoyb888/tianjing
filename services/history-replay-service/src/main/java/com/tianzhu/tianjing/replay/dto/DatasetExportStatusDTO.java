package com.tianzhu.tianjing.replay.dto;

/**
 * 数据集导出进度响应
 * 对应 GET /simulations/{task_id}/export-status
 */
public record DatasetExportStatusDTO(

        /** 导出状态：PENDING / EXPORTING / EXPORTED / EXPORT_FAILED */
        String status,

        /** 进度百分比 0~100 */
        int progress,

        /** 仿真任务总帧数 */
        int totalFrames,

        /** 已导出帧数 */
        int exportedFrames,

        /** 生成的标注框总数 */
        int annotationCount,

        /** 目标数据集版本 ID */
        String datasetVersionId,

        /** 导出 zip 在 MinIO 中的路径，导出完成后有值 */
        String minioPath,

        /** 错误信息，EXPORT_FAILED 时有值 */
        String errorMsg
) {}
