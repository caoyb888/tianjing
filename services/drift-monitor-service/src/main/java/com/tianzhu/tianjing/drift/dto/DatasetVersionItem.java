package com.tianzhu.tianjing.drift.dto;

/**
 * 数据集版本简要信息 — 供提交训练作业表单下拉选择
 */
public record DatasetVersionItem(
        String versionId,
        String versionTag,
        String datasetCode,
        boolean isFrozen
) {}
