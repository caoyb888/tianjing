package com.tianzhu.tianjing.replay.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
/**
 * POST /simulations 请求体
 * 前端在视频上传完成后调用，携带 scene_id、已上传视频的 URL、推理插件和抽帧频率
 */
public record SimulationCreateRequest(
        @NotBlank @JsonProperty("scene_id")  String sceneId,
        @NotBlank @JsonProperty("video_url") String videoUrl,
        /** 推理插件 ID，默认 CLOUD-PROXY-V1 */
        @JsonProperty("plugin_id") String pluginId,
        /** 抽帧频率（帧/秒），支持 1/2/5，默认 1 */
        @JsonProperty("frame_fps") Integer frameFps
) {}
