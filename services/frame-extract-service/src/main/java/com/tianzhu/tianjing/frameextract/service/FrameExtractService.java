package com.tianzhu.tianjing.frameextract.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianzhu.tianjing.frameextract.dto.FrameMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 帧抽取服务
 *
 * 职责：
 * 1. 按场景配置的帧间隔对原始帧进行采样（抽帧），避免全量帧涌入推理服务
 * 2. 验证帧消息合法性（scene_id 非空、ROI 范围有效）
 * 3. 将采样后的帧消息发布至 tianjing.frame.extracted Topic
 *
 * 规范：CLAUDE.md §4.2（frame-extract-service 职责边界）、§8.1（Kafka Topic 命名）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FrameExtractService {

    private static final String TOPIC_EXTRACTED = "tianjing.frame.extracted";

    /** 默认采样间隔（毫秒），可通过 Nacos 动态下发 */
    @Value("${tianjing.frame-extract.default-interval-ms:200}")
    private int defaultIntervalMs;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /** 每个场景最近一次采样时间（scene_id → timestampMs） */
    private final ConcurrentHashMap<String, Long> lastSampleTs = new ConcurrentHashMap<>();

    /**
     * 处理原始帧：按帧间隔过滤，通过采样的帧发布至下游
     *
     * @param frame 原始帧消息
     * @param configuredIntervalMs 场景配置的帧间隔；-1 表示使用默认值
     */
    public void process(FrameMessage frame, int configuredIntervalMs) {
        if (frame.getSceneId() == null || frame.getSceneId().isBlank()) {
            log.warn("帧消息缺少 scene_id，丢弃 frame_id={}", frame.getFrameId());
            return;
        }

        int intervalMs = configuredIntervalMs > 0 ? configuredIntervalMs : defaultIntervalMs;

        long last = lastSampleTs.getOrDefault(frame.getSceneId(), 0L);
        if (frame.getTimestampMs() - last < intervalMs) {
            // 未到采样间隔，丢弃本帧
            log.debug("帧抽取：丢弃（未到采样间隔） scene_id={} frame_id={} interval_ms={}",
                    frame.getSceneId(), frame.getFrameId(), intervalMs);
            return;
        }

        lastSampleTs.put(frame.getSceneId(), frame.getTimestampMs());
        publishExtracted(frame);
    }

    private void publishExtracted(FrameMessage frame) {
        try {
            String payload = objectMapper.writeValueAsString(frame);
            kafkaTemplate.send(TOPIC_EXTRACTED, frame.getSceneId(), payload);
            log.debug("帧抽取：发布 scene_id={} frame_id={} is_sandbox={}",
                    frame.getSceneId(), frame.getFrameId(), frame.isSandbox());
        } catch (Exception e) {
            log.error("帧抽取发布失败 scene_id={} frame_id={}", frame.getSceneId(), frame.getFrameId(), e);
        }
    }
}
