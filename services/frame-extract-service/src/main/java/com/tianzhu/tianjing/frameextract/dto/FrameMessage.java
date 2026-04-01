package com.tianzhu.tianjing.frameextract.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 帧消息 DTO — 与 stream-ingest-service 发布的消息结构严格对齐
 * 规范：CLAUDE.md §8.3 消息 Schema
 */
@Data
public class FrameMessage {
    @JsonProperty("frame_id")
    private String frameId;
    @JsonProperty("scene_id")
    private String sceneId;
    @JsonProperty("timestamp_ms")
    private long timestampMs;
    @JsonProperty("is_sandbox")
    private boolean isSandbox;
    @JsonProperty("image_url")
    private String imageUrl;
    @JsonProperty("image_width")
    private int imageWidth;
    @JsonProperty("image_height")
    private int imageHeight;
    private Roi roi;

    @Data
    public static class Roi {
        private int x;
        private int y;
        private int w;
        private int h;
    }
}
