package com.tianzhu.tianjing.drift.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
public record TrainJobRequest(
    @NotBlank @JsonProperty("plugin_id") String pluginId,
    @NotBlank @JsonProperty("dataset_version_id") String datasetVersionId,
    @JsonProperty("train_config_json") Object trainConfigJson,
    @JsonProperty("gpu_count") Integer gpuCount,
    @JsonProperty("trigger_reason") String triggerReason
) {}
