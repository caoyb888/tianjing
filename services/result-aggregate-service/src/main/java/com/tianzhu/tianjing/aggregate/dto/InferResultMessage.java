package com.tianzhu.tianjing.aggregate.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 推理结果消息 DTO
 * 规范：CLAUDE.md §8.3 推理结果消息 Schema
 *
 * 消费 Topic：
 *   tianjing.infer.result.production（is_sandbox=false）
 *   tianjing.infer.result.sandbox  （is_sandbox=true）
 */
@Data
public class InferResultMessage {
    @JsonProperty("frame_id")
    private String frameId;
    @JsonProperty("scene_id")
    private String sceneId;
    @JsonProperty("plugin_id")
    private String pluginId;
    @JsonProperty("is_sandbox")
    private boolean isSandbox;
    private List<Detection> detections;
    @JsonProperty("inference_time_ms")
    private double inferenceTimeMs;
    @JsonProperty("timestamp_ms")
    private long timestampMs;
    /** 厂部（来自场景配置，由推理服务透传） */
    private String factory;

    @Data
    public static class Detection {
        @JsonProperty("class_id")
        private int classId;
        @JsonProperty("class_name")
        private String className;
        private double confidence;
        private BBox bbox;

        @Data
        public static class BBox {
            private float x1;
            private float y1;
            private float x2;
            private float y2;
        }
    }
}
