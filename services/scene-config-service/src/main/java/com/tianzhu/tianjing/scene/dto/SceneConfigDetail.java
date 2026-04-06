package com.tianzhu.tianjing.scene.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tianzhu.tianjing.scene.domain.SceneConfig;

import java.time.OffsetDateTime;

/**
 * 场景配置响应体（SceneConfigDetail）
 * 规范：API 接口规范 V3.1 §7 Schema 定义
 * 字段名与前端 SceneConfig 类型严格对齐（camelCase，枚举值小写）
 */
public record SceneConfigDetail(
        String sceneId,
        @JsonProperty("name") String sceneName,
        @JsonProperty("factory") String factoryCode,
        String processCode,
        String category,
        String priority,
        String status,
        String boundDeviceCode,
        String activeModelVersionId,
        String activePluginId,
        Integer frameInterval,
        @JsonProperty("alarmConfig") Object alarmConfigJson,
        @JsonProperty("algorithmConfig") Object algoParamsJson,
        Object workflowJson,
        Integer version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String createdBy
) {
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /** 将 JSONB 字符串安全反序列化为 Object；失败时原样返回字符串 */
    private static Object parseJson(String raw) {
        if (raw == null) return null;
        try {
            return MAPPER.readValue(raw, Object.class);
        } catch (Exception ignored) {
            return raw;
        }
    }

    public static SceneConfigDetail from(SceneConfig s) {
        return new SceneConfigDetail(
                s.getSceneId(), s.getSceneName(),
                normalizeFactory(s.getFactoryCode()),
                s.getProcessCode(),
                normalizeCategory(s.getCategory()),
                s.getPriority(),
                s.getStatus() != null ? s.getStatus().toLowerCase() : null,
                s.getBoundDeviceCode(), s.getActiveModelVersionId(), s.getActivePluginId(),
                s.getFrameInterval(),
                parseJson(s.getAlarmConfigJson()),
                parseJson(s.getAlgoParamsJson()),
                parseJson(s.getWorkflowJson()),
                s.getVersion(),
                s.getCreatedAt(), s.getUpdatedAt(), s.getCreatedBy()
        );
    }

    /** DB factory_code（大写）→ 前端 Factory 枚举值（小写） */
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

    /** DB category（大写+下划线）→ 前端 SceneCategory 枚举值（小写） */
    private static String normalizeCategory(String dbValue) {
        if (dbValue == null) return null;
        return switch (dbValue) {
            case "QUALITY_INSPECT"   -> "quality";
            case "EQUIPMENT_MONITOR" -> "equipment";
            case "PROCESS_PARAM"     -> "process";
            default                  -> dbValue.toLowerCase();
        };
    }
}
