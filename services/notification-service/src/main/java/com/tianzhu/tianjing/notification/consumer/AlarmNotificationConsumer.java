package com.tianzhu.tianjing.notification.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 告警推送消费者
 *
 * 安全约束（第二道防线）：
 *   is_sandbox=true 的消息绝不推送至外部系统（规范：CLAUDE.md §11.1）
 *   alarm-judge-service 的 SandboxInterceptor 是第一道拦截，此处为防御性校验。
 *
 * 规范：CLAUDE.md §8.2（消费者组命名），§10.3（安全）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmNotificationConsumer {

    private final ObjectMapper objectMapper;

    /**
     * 消费 CRITICAL 级别告警
     * 规范：CLOUD.md §8.2 消费者组命名 {service-name}-{env}-cg
     */
    @KafkaListener(
            topics = "tianjing.alarm.critical",
            groupId = "notification-service-prod-cg"
    )
    public void onCriticalAlarm(ConsumerRecord<String, String> record) {
        processAlarm(record, "CRITICAL");
    }

    /**
     * 消费 WARNING 级别告警
     */
    @KafkaListener(
            topics = "tianjing.alarm.warning",
            groupId = "notification-service-prod-cg"
    )
    public void onWarningAlarm(ConsumerRecord<String, String> record) {
        processAlarm(record, "WARNING");
    }

    private void processAlarm(ConsumerRecord<String, String> record, String expectedLevel) {
        try {
            JsonNode alarm = objectMapper.readTree(record.value());

            // SECURITY: 第二道防线 — 防御性 Sandbox 校验（规范：CLAUDE.md §11.1）
            // 即使 alarm-judge-service 的 SandboxInterceptor 已拦截，此处仍需校验
            boolean isSandbox = alarm.path("is_sandbox").asBoolean(true); // 默认 true = 安全降级
            if (isSandbox) {
                // SECURITY: Sandbox 消息不得推送，记录安全警告
                log.error("SECURITY ALERT: notification-service 收到 is_sandbox=true 的告警消息，" +
                          "已拦截！alarm-judge-service 的 SandboxInterceptor 可能存在缺陷。" +
                          "sceneId={}, alarmId={}",
                        alarm.path("scene_id").asText(), alarm.path("alarm_id").asText());
                return;
            }

            String sceneId = alarm.path("scene_id").asText();
            String alarmId = alarm.path("alarm_id").asText();
            String alarmLevel = alarm.path("alarm_level").asText();

            log.info("处理告警推送",
                    "alarmId", alarmId,
                    "sceneId", sceneId,
                    "level", alarmLevel);

            // TODO Sprint 3: 实现 MQTT 推送、企业微信、工单系统对接
            // 当前返回成功消费（防止积压），推送能力 Sprint 3 实现

        } catch (Exception e) {
            log.error("告警消息解析失败", "offset", record.offset(), "error", e.getMessage());
        }
    }
}
