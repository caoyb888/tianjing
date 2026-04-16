package com.tianzhu.tianjing.replay.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
/**
 * POST /simulations 请求体
 * 前端在视频上传完成后调用，携带 scene_id、已上传视频的 URL、推理插件和抽帧频率
 * 支持一次创建时传入多个视频（第一个 + extraVideos）
 */
public record SimulationCreateRequest(
        @NotBlank @JsonProperty("scene_id")  String sceneId,
        @NotBlank @JsonProperty("video_url") String videoUrl,
        /** 第一个视频的标注类型：NORMAL / ABNORMAL / MIXED（默认 MIXED） */
        @JsonProperty("video_label") String videoLabel,
        /** 推理插件 ID，默认 CLOUD-PROXY-V1 */
        @JsonProperty("plugin_id") String pluginId,
        /** 抽帧频率（帧/秒），支持 1/2/5，默认 1 */
        @JsonProperty("frame_fps") Integer frameFps,
        /** 额外追加的视频列表（可选，支持一次上传多个） */
        @JsonProperty("extra_videos") List<ExtraVideo> extraVideos
) {
    /** 额外视频项 */
    public record ExtraVideo(
            @NotBlank @JsonProperty("video_url") String videoUrl,
            /** NORMAL / ABNORMAL / MIXED */
            @JsonProperty("label") String label
    ) {}
}
