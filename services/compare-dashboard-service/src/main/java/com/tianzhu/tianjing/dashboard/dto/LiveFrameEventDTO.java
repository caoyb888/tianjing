package com.tianzhu.tianjing.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 实时推理帧 SSE 事件 DTO
 * 由 compare-dashboard-service 消费 tianjing.infer.result.production 后，
 * 通过 SSE 推送至大屏前端，供实时推理画面渲染（帧图 + 检测框叠加）。
 *
 * 规范：CLAUDE.md §8.3（推理结果消息 Schema 扩展）
 */
@Data
@Builder
public class LiveFrameEventDTO {

    @JsonProperty("frame_id")
    private String frameId;

    @JsonProperty("scene_id")
    private String sceneId;

    /**
     * 原始帧 MinIO URL（minio://bucket/path）。
     * 前端将 minio:// 替换为 /minio-frames/ 以通过 Vite/Nginx 代理访问 MinIO。
     */
    @JsonProperty("image_url")
    private String imageUrl;

    /** 检测框列表（格式与 InferResponse.Detection 一致） */
    private List<Map<String, Object>> detections;

    @JsonProperty("inference_time_ms")
    private double inferenceTimeMs;

    @JsonProperty("dispatcher_total_ms")
    private double dispatcherTotalMs;

    private String backend;

    @JsonProperty("timestamp_ms")
    private long timestampMs;

    @JsonProperty("is_sandbox")
    private boolean isSandbox;
}
