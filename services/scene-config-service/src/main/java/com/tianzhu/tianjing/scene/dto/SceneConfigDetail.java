package com.tianzhu.tianjing.scene.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tianzhu.tianjing.scene.domain.SceneConfig;

import java.time.OffsetDateTime;

/**
 * 场景配置响应体（SceneConfigDetail）
 * 规范：API 接口规范 V3.1 §7 Schema 定义
 */
public record SceneConfigDetail(
        @JsonProperty("scene_id") String sceneId,
        @JsonProperty("scene_name") String sceneName,
        @JsonProperty("factory_code") String factoryCode,
        @JsonProperty("process_code") String processCode,
        String category,
        String priority,
        String status,
        @JsonProperty("bound_device_code") String boundDeviceCode,
        @JsonProperty("active_model_version_id") String activeModelVersionId,
        @JsonProperty("active_plugin_id") String activePluginId,
        @JsonProperty("frame_interval") Integer frameInterval,
        @JsonProperty("alarm_config_json") Object alarmConfigJson,
        @JsonProperty("algo_params_json") Object algoParamsJson,
        Integer version,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("updated_at") OffsetDateTime updatedAt,
        @JsonProperty("created_by") String createdBy
) {
    public static SceneConfigDetail from(SceneConfig s) {
        return new SceneConfigDetail(
                s.getSceneId(), s.getSceneName(), s.getFactoryCode(), s.getProcessCode(),
                s.getCategory(), s.getPriority(), s.getStatus(),
                s.getBoundDeviceCode(), s.getActiveModelVersionId(), s.getActivePluginId(),
                s.getFrameInterval(),
                s.getAlarmConfigJson(), s.getAlgoParamsJson(),
                s.getVersion(),
                s.getCreatedAt(), s.getUpdatedAt(), s.getCreatedBy()
        );
    }
}
