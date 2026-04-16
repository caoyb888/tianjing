package com.tianzhu.tianjing.aggregate.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianzhu.tianjing.aggregate.dto.InferResultMessage;
import com.tianzhu.tianjing.aggregate.service.ResultAggregateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 推理结果 Kafka 消费者
 *
 * 消费两个 Topic（生产与 Sandbox 严格分开，使用不同消费者组）：
 *   tianjing.infer.result.production → result-aggregate-service-prod-cg
 *   tianjing.infer.result.sandbox    → result-aggregate-service-sandbox-cg
 *
 * 规范：CLAUDE.md §8.2（消费者组命名）、§11.1（is_sandbox 传递链路完整性）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InferResultConsumer {

    private final ResultAggregateService resultAggregateService;
    private final ObjectMapper objectMapper;

    /** 生产推理结果消费者 */
    @KafkaListener(
            topics = "tianjing.infer.result.production",
            groupId = "result-aggregate-service-prod-cg",
            concurrency = "4"
    )
    public void onProductionResult(String payload,
                                   @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        try {
            InferResultMessage result = objectMapper.readValue(payload, InferResultMessage.class);
            resultAggregateService.handleProductionResult(result);
        } catch (Exception e) {
            log.error("生产推理结果反序列化失败 partition={} payload={}", partition, payload, e);
        }
    }

    /**
     * Sandbox 推理结果消费者
     * 规范：CLAUDE.md §8.2 — Sandbox 消费者只消费 sandbox topic，不得订阅生产 topic
     */
    @KafkaListener(
            topics = "tianjing.infer.result.sandbox",
            groupId = "result-aggregate-service-sandbox-cg",
            concurrency = "2"
    )
    public void onSandboxResult(String payload,
                                @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        try {
            InferResultMessage result = objectMapper.readValue(payload, InferResultMessage.class);
            resultAggregateService.handleSandboxResult(result);
        } catch (Exception e) {
            log.error("Sandbox 推理结果反序列化失败 partition={} payload={}", partition, payload, e);
        }
    }
}
