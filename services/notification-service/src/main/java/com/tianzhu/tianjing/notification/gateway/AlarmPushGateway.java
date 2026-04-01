package com.tianzhu.tianjing.notification.gateway;

/**
 * 告警推送网关接口
 *
 * Sprint 3 实现：MQTT（EMQX）、企业微信、工单系统、SCADA 联动
 * 规范：CLAUDE.md §9.3（工业互联网平台对接接口）、§2.5（MQTT EMQX 5.x）
 *
 * 硬性约束：is_sandbox=true 的消息绝不通过此接口推送（由 NotificationService 在调用前拦截）
 */
public interface AlarmPushGateway {

    /**
     * 推送 CRITICAL 级别告警
     * 触发：MQTT + 企业微信 + 工单 + SCADA 联动（规范：CLAUDE.md 附录告警级别判定参考）
     */
    void pushCritical(String alarmId, String sceneId, String alarmJson);

    /**
     * 推送 WARNING 级别告警
     * 触发：WebSocket + 移动端推送
     */
    void pushWarning(String alarmId, String sceneId, String alarmJson);
}
