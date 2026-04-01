package com.tianzhu.tianjing.preprocess.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianzhu.tianjing.preprocess.dto.FrameMessage;
import com.tianzhu.tianjing.preprocess.service.PreprocessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 图像预处理 Kafka 消费者
 *
 * 消费 tianjing.frame.extracted（帧抽取服务输出）
 * 规范：CLAUDE.md §8.2 消费者组命名
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreprocessConsumer {

    private final PreprocessService preprocessService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "tianjing.frame.extracted",
            groupId = "preprocess-service-prod-cg",
            concurrency = "4"
    )
    public void onExtractedFrame(String payload,
                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                 @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        try {
            FrameMessage frame = objectMapper.readValue(payload, FrameMessage.class);
            preprocessService.process(frame);
        } catch (Exception e) {
            log.error("预处理消息反序列化失败 topic={} partition={}", topic, partition, e);
        }
    }
}
