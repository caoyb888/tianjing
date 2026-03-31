package com.tianzhu.tianjing.dashboard.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
public record SandboxStartRequest(
    @NotBlank @JsonProperty("scene_id") String sceneId,
    @JsonProperty("production_model_version_id") String productionModelVersionId,
    @NotBlank @JsonProperty("experiment_model_version_id") String experimentModelVersionId
) {}
