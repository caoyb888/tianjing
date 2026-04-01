package com.tianzhu.tianjing.replay.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
/**
 * POST /simulations 请求体
 * 前端在视频上传完成后调用，携带 scene_id 和已上传视频的 URL
 */
public record SimulationCreateRequest(
        @NotBlank @JsonProperty("scene_id")  String sceneId,
        @NotBlank @JsonProperty("video_url") String videoUrl
) {}
