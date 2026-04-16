package com.tianzhu.tianjing.frameextract.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianzhu.tianjing.frameextract.dto.FrameMessage;
import com.tianzhu.tianjing.frameextract.service.FrameExtractService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 帧抽取 Kafka 消费者
 *
 * 消费 tianjing.frame.production（stream-ingest-service 发布的原始帧）
 * 规范：CLAUDE.md §8.2 消费者组命名：{service-name}-{env}-cg
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FrameExtractConsumer {

    private final FrameExtractService frameExtractService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "tianjing.frame.production",
            groupId = "frame-extract-service-prod-cg",
            concurrency = "4"
    )
    public void onFrame(String payload,
                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        try {
            FrameMessage frame = objectMapper.readValue(payload, FrameMessage.class);
            // 使用默认采样间隔（实际项目中从 Nacos 场景配置读取）
            frameExtractService.process(frame, -1);
        } catch (Exception e) {
            log.error("帧消息反序列化失败 topic={} partition={} payload={}",
                    topic, partition, payload, e);
        }
    }
}
