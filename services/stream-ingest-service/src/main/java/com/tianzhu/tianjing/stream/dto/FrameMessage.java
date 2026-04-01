package com.tianzhu.tianjing.stream.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 推理帧消息 — Kafka Topic: tianjing.frame.production
 * 规范：CLAUDE.md §8.3 消息 Schema
 */
@Data
@Builder
public class FrameMessage {
    private String frameId;
    private String sceneId;
    private long   timestampMs;
    private boolean isSandbox;
    private String imageUrl;
    private int    imageWidth;
    private int    imageHeight;
    private Roi    roi;

    @Data
    @Builder
    public static class Roi {
        private int x;
        private int y;
        private int w;
        private int h;
    }
}
