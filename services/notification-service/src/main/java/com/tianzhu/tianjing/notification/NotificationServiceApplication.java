package com.tianzhu.tianjing.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 推送通知服务
 *
 * 职责：消费 tianjing.alarm.critical/warning Topic，推送至 MQTT(EMQX)/企业微信/工单系统
 *
 * 安全约束：
 *   - 必须有防御性校验，拒绝 is_sandbox=true 的消息（规范：CLAUDE.md §11.1）
 *   - alarm-judge-service 的 SandboxInterceptor 是第一道门，此处为第二道防御
 *
 * 规范：CLAUDE.md §4.2（notification-service）
 */
@SpringBootApplication(scanBasePackages = {
        "com.tianzhu.tianjing.notification",
        "com.tianzhu.tianjing.common"
})
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
