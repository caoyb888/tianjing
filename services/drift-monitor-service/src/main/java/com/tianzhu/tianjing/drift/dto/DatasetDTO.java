package com.tianzhu.tianjing.drift.dto;

import com.tianzhu.tianjing.drift.domain.Dataset;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 数据集响应 DTO — 字段名与前端约定对齐
 * name/factory/imageCount/annotationCount/versions/createdAt
 */
public record DatasetDTO(
        String datasetCode,
        String name,
        String factory,
        int imageCount,
        int annotationCount,
        List<String> versions,
        OffsetDateTime createdAt
) {
    public static DatasetDTO of(Dataset d, List<String> versions) {
        return new DatasetDTO(
                d.getDatasetCode(),
                d.getDatasetName(),
                normalizeFactory(d.getFactoryCode()),
                d.getTotalSamples() != null ? d.getTotalSamples() : 0,
                d.getPositiveSamples() != null ? d.getPositiveSamples() : 0,
                versions,
                d.getCreatedAt()
        );
    }

    /** DB factory_code（大写）→ 前端 Factory 枚举值（小写），与 SceneConfigDetail.normalizeFactory 保持一致 */
    private static String normalizeFactory(String dbValue) {
        if (dbValue == null) return null;
        return switch (dbValue.toUpperCase()) {
            case "PELLET"  -> "pellet";
            case "SINTER"  -> "sintering";
            case "STEEL"   -> "steel";
            case "SECTION" -> "section";
            case "STRIP"   -> "strip";
            default        -> dbValue.toLowerCase();
        };
    }
}
