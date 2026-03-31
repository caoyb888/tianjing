package com.tianzhu.tianjing.algomodel.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
public record ModelRegisterRequest(
    @NotBlank @JsonProperty("plugin_id") String pluginId,
    @NotBlank String version,
    @JsonProperty("mlflow_run_id") String mlflowRunId,
    @JsonProperty("model_artifact_url") String modelArtifactUrl,
    @JsonProperty("training_job_id") String trainingJobId
) {}
