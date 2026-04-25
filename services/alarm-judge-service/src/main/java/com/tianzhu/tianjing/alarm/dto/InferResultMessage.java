package com.tianzhu.tianjing.alarm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 推理结果消息 DTO（alarm-judge-service 告警判定消费端）
 * 规范：CLAUDE.md §8.3 推理结果消息 Schema
 *
 * 同时支持：
 *   - 检测插件（YOLO 等）：detections 列表
 *   - 分类插件（CLASSIFY-*）：classifications 列表
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
    private boolean sandbox;

    private List<Detection> detections;

    /** 分类插件（CLASSIFY-*）输出；检测插件此字段为空或 null */
    private List<Classification> classifications;

    @JsonProperty("inference_time_ms")
    private double inferenceTimeMs;

    @JsonProperty("timestamp_ms")
    private long timestampMs;

    @JsonProperty("image_url")
    private String imageUrl;

    private String factory;

    private String backend;

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

    @Data
    public static class Classification {
        @JsonProperty("class_id")
        private int classId;
        @JsonProperty("class_name")
        private String className;
        private double confidence;
    }
}
