package com.tianzhu.tianjing.alarm.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianzhu.tianjing.alarm.dto.InferResultMessage;
import com.tianzhu.tianjing.alarm.service.AlarmJudgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 推理结果 Kafka 消费者 — 告警判定入口
 *
 * 消费 tianjing.infer.result.production，对每帧推理结果执行告警判定。
 * 规范：CLAUDE.md §8.2（消费者组命名）、§11.1（is_sandbox 链路完整性）
 *
 * 注意：不消费 sandbox topic。Sandbox 结果由 result-aggregate-service 写 TDengine，
 * 告警判定服务仅处理生产推理结果，SECURITY: Sandbox 拦截在 AlarmJudgeService 内部实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InferResultConsumer {

    private final AlarmJudgeService alarmJudgeService;
    private final ObjectMapper objectMapper;

    /**
     * 生产推理结果消费者
     * 消费者组命名规范：{service-name}-{env}-cg（CLAUDE.md §8.2）
     */
    @KafkaListener(
            topics = "tianjing.infer.result.production",
            groupId = "alarm-judge-service-prod-cg",
            concurrency = "2"
    )
    public void onProductionResult(String payload,
                                   @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        InferResultMessage result = null;
        try {
            result = objectMapper.readValue(payload, InferResultMessage.class);
        } catch (Exception e) {
            log.error("推理结果反序列化失败 partition={} error={}", partition, e.getMessage());
            return;
        }

        try {
            alarmJudgeService.judge(result);
        } catch (Exception e) {
            log.error("告警判定异常 scene_id={} frame_id={} error={}",
                    result.getSceneId(), result.getFrameId(), e.getMessage(), e);
        }
    }
}
