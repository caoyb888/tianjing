package com.tianzhu.tianjing.stream.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianzhu.tianjing.stream.dto.FrameMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 推理帧 Kafka 发布器
 * 规范：CLAUDE.md §8.1 — 生产帧写入 tianjing.frame.production（is_sandbox=false）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaFramePublisher {

    private static final String TOPIC_PRODUCTION = "tianjing.frame.production";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 发布帧消息到生产 Topic，按 scene_id 哈希分区（规范：CLAUDE.md §8.1）
     */
    public void publish(FrameMessage msg) {
        try {
            String payload = objectMapper.writeValueAsString(msg);
            kafkaTemplate.send(TOPIC_PRODUCTION, msg.getSceneId(), payload);
            log.debug("发布帧消息 scene_id={} frame_id={} is_sandbox={}",
                    msg.getSceneId(), msg.getFrameId(), msg.isSandbox());
        } catch (Exception e) {
            log.error("帧消息序列化失败 scene_id={} frame_id={}", msg.getSceneId(), msg.getFrameId(), e);
        }
    }
}
