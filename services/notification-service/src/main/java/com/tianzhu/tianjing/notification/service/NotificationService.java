package com.tianzhu.tianjing.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianzhu.tianjing.notification.gateway.AlarmPushGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 告警推送业务服务
 *
 * 安全约束（第二道防线）：
 *   is_sandbox=true 的消息绝不推送至外部系统（规范：CLAUDE.md §11.1）
 *   alarm-judge-service 的 SandboxInterceptor 是第一道拦截，此处为防御性校验。
 *
 * INFO 级别告警：仅写数据库，不通过推送网关（规范：CLAUDE.md 附录告警级别判定参考）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final ObjectMapper objectMapper;
    private final AlarmPushGateway pushGateway;

    /**
     * 处理告警推送
     *
     * @param alarmJson    告警消息 JSON 字符串
     * @param level        期望告警级别（CRITICAL / WARNING / INFO）
     */
    public void processAlarm(String alarmJson, String level) {
        try {
            JsonNode alarm = objectMapper.readTree(alarmJson);

            // SECURITY: 第二道防线 — 防御性 Sandbox 校验（规范：CLAUDE.md §11.1）
            // 默认值设为 true（安全降级），确保缺少字段时不会意外推送
            boolean isSandbox = alarm.path("is_sandbox").asBoolean(true);
            if (isSandbox) {
                // SECURITY: Sandbox 消息不得推送，记录安全警告
                log.error("SECURITY ALERT: notification-service 收到 is_sandbox=true 的告警消息，已拦截！" +
                          "alarm-judge-service 的 SandboxInterceptor 可能存在缺陷。" +
                          "sceneId={}, alarmId={}",
                        alarm.path("scene_id").asText(), alarm.path("alarm_id").asText());
                return;
            }

            String alarmId = alarm.path("alarm_id").asText();
            String sceneId = alarm.path("scene_id").asText();

            log.info("处理告警推送, alarmId={}, sceneId={}, level={}", alarmId, sceneId, level);

            if ("CRITICAL".equals(level)) {
                // CRITICAL: MQTT + 企业微信 + 工单 + SCADA 联动（Sprint 3 实现）
                pushGateway.pushCritical(alarmId, sceneId, alarmJson);
            } else if ("WARNING".equals(level)) {
                // WARNING: WebSocket + 移动端推送（Sprint 3 实现）
                pushGateway.pushWarning(alarmId, sceneId, alarmJson);
            }
            // INFO 级别：仅写数据库，不推送（由上游 alarm-judge-service 持久化，此处跳过）

        } catch (Exception e) {
            log.error("告警消息处理失败, level={}, error={}", level, e.getMessage(), e);
        }
    }
}
