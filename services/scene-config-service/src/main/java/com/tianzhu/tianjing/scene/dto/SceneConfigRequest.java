package com.tianzhu.tianjing.scene.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 新增/更新场景配置请求体
 * PUT 时需额外携带 version 和 change_desc
 */
public record SceneConfigRequest(
        @NotBlank(message = "场景名称不能为空")
        @JsonProperty("scene_name") String sceneName,

        @NotBlank(message = "厂部编码不能为空")
        @JsonProperty("factory_code") String factoryCode,

        @JsonProperty("process_code") String processCode,

        @NotBlank(message = "场景分类不能为空")
        String category,

        String priority,

        @JsonProperty("frame_interval") Integer frameInterval,

        @JsonProperty("alarm_config_json") Object alarmConfigJson,

        @JsonProperty("algo_params_json") Object algoParamsJson,

        // PUT 专用字段（乐观锁）
        Integer version,

        @JsonProperty("change_desc") String changeDesc
) {}
