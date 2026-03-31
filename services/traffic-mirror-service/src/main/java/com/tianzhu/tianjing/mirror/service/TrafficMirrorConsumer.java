package com.tianzhu.tianjing.mirror.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * T型分流镜像消费者
 *
 * 职责：
 *   - 消费 tianjing.frame.production（5fps 采样，即每 5 帧取 1 帧）
 *   - 复制至 tianjing.frame.sandbox，设置 is_sandbox=true
 *   - 绝不修改原始帧数据，绝不影响生产 Topic 消费
 *
 * 规范：CLAUDE.md §4.2（traffic-mirror-service），§8.1，§11.1
 */
@Slf4j
@Component
public class TrafficMirrorConsumer {

    /** Sandbox 采样率：每 5 帧取 1 帧（规范：CLAUDE.md §8.1 sandbox 5fps） */
    private static final int MIRROR_SAMPLE_RATE = 5;

    private static final String SANDBOX_TOPIC = "tianjing.frame.sandbox";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Counter mirroredCounter;
    private final AtomicLong frameSeq = new AtomicLong(0);

    public TrafficMirrorConsumer(KafkaTemplate<String, String> kafkaTemplate,
                                 ObjectMapper objectMapper,
                                 MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.mirroredCounter = Counter.builder("tianjing_frame_mirrored_total")
                .description("镜像至 Sandbox 的帧数量")
                .register(meterRegistry);
    }

    /**
     * 消费生产帧，5:1 降频镜像至 Sandbox Topic
     *
     * 注意：此消费者使用独立消费者组，不影响其他消费者（如 route-dispatch-service）
     * 规范：CLAUDE.md §8.2
     */
    @KafkaListener(
            topics = "tianjing.frame.production",
            groupId = "traffic-mirror-service-prod-cg"
    )
    public void onProductionFrame(ConsumerRecord<String, String> record) {
        long seq = frameSeq.incrementAndGet();

        // 5:1 采样（保留生产 25fps 的 5fps 镜像）
        if (seq % MIRROR_SAMPLE_RATE != 0) {
            return;
        }

        try {
            ObjectNode frameMsg = (ObjectNode) objectMapper.readTree(record.value());

            // SECURITY: 强制设置 is_sandbox=true（规范：CLAUDE.md §11.1）
            // 镜像帧必须标记为 Sandbox，绝不允许触发生产告警
            frameMsg.put("is_sandbox", true);

            String sceneId = frameMsg.path("scene_id").asText("");
            String mirroredJson = objectMapper.writeValueAsString(frameMsg);

            // key = sceneId，保证同一场景的 Sandbox 帧有序（规范：CLAUDE.md §8.1）
            kafkaTemplate.send(SANDBOX_TOPIC, sceneId, mirroredJson);
            mirroredCounter.increment();

            log.debug("帧镜像至 Sandbox",
                    "sceneId", sceneId,
                    "frameId", frameMsg.path("frame_id").asText(),
                    "seq", seq);

        } catch (Exception e) {
            log.error("帧镜像处理失败", "offset", record.offset(), "error", e.getMessage());
        }
    }
}
