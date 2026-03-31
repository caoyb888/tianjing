package com.tianzhu.tianjing.replay.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
public record SimulationCreateRequest(
    @NotBlank @JsonProperty("scene_id") String sceneId,
    @NotBlank @JsonProperty("video_file_name") String videoFileName,
    @NotBlank @JsonProperty("video_object_path") String videoObjectPath
) {}
