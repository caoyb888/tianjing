package com.tianzhu.tianjing.preprocess.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 帧消息 DTO（预处理阶段）
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
    /** 预处理元数据（去雾/增强等算法参数，供 Python 推理服务消费） */
    @JsonProperty("preprocess_meta")
    private Map<String, Object> preprocessMeta = new HashMap<>();

    @Data
    public static class Roi {
        private int x;
        private int y;
        private int w;
        private int h;
    }
}
