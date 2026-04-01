package com.tianzhu.tianjing.notification.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AlarmPushGateway 本地开发 Stub 实现
 *
 * Sprint 3 实现真实推送（MQTT EMQX、企业微信、工单系统）前，
 * 此 stub 仅记录日志，防止服务启动失败。
 *
 * 规范：CLAUDE.md §9.3（Sprint 3 实现）
 */
@Slf4j
@Component
public class StubAlarmPushGateway implements AlarmPushGateway {

    @Override
    public void pushCritical(String alarmId, String sceneId, String alarmJson) {
        // TODO Sprint 3: 实现 MQTT + 企业微信 + 工单 + SCADA 联动
        log.info("[STUB] CRITICAL 告警推送（Sprint 3 待实现）alarmId={} sceneId={}", alarmId, sceneId);
    }

    @Override
    public void pushWarning(String alarmId, String sceneId, String alarmJson) {
        // TODO Sprint 3: 实现 WebSocket + 移动端推送
        log.info("[STUB] WARNING 告警推送（Sprint 3 待实现）alarmId={} sceneId={}", alarmId, sceneId);
    }
}
