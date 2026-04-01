package com.tianzhu.tianjing.notification.consumer;

import com.tianzhu.tianjing.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 告警推送消费者
 *
 * 职责：从 Kafka 消费告警消息，委托 NotificationService 处理推送逻辑。
 * Sandbox 拦截逻辑已封装在 NotificationService 中（防御性第二道防线）。
 *
 * 规范：CLAUDE.md §8.2（消费者组命名），§11.1（Sandbox 拦截）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlarmNotificationConsumer {

    private final NotificationService notificationService;

    /**
     * 消费 CRITICAL 级别告警
     * 规范：CLAUDE.md §8.2 消费者组命名 {service-name}-{env}-cg
     */
    @KafkaListener(
            topics = "tianjing.alarm.critical",
            groupId = "notification-service-prod-cg"
    )
    public void onCriticalAlarm(ConsumerRecord<String, String> record) {
        notificationService.processAlarm(record.value(), "CRITICAL");
    }

    /**
     * 消费 WARNING 级别告警
     */
    @KafkaListener(
            topics = "tianjing.alarm.warning",
            groupId = "notification-service-prod-cg"
    )
    public void onWarningAlarm(ConsumerRecord<String, String> record) {
        notificationService.processAlarm(record.value(), "WARNING");
    }
}
