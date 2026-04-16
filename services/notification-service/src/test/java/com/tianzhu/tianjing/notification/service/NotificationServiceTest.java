package com.tianzhu.tianjing.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianzhu.tianjing.notification.gateway.AlarmPushGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 告警推送服务单元测试
 *
 * P0 安全用例：is_sandbox=true 的消息绝不触发外部推送（规范：CLAUDE.md §11.1、§12.2）
 * 规范：CLAUDE.md §12.1（告警判定服务覆盖率 ≥ 95%，Sandbox 拦截 100%）
 * 测试计划：天柱天镜_测试推进计划 §3.1 M1 修复期高优先级
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService — Sandbox 拦截 + 告警分级推送")
class NotificationServiceTest {

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AlarmPushGateway pushGateway;

    @InjectMocks
    private NotificationService notificationService;

    // ── P0 安全用例：Sandbox 拦截 ──────────────────────────────

    /**
     * P0 核心安全测试：is_sandbox=true 的 CRITICAL 告警绝不触发推送
     * 违反此约束视为 P0 安全红线（CLAUDE.md §15）
     */
    @Test
    @DisplayName("P0: processAlarm — is_sandbox=true 的 CRITICAL 消息绝不调用 pushGateway")
    void processAlarm_sandboxCritical_neverPushes() {
        String sandboxAlarm = """
                {
                  "alarm_id": "ALM-20260401-SBX-001",
                  "scene_id": "SCENE-SINTER-005",
                  "alarm_level": "CRITICAL",
                  "is_sandbox": true,
                  "confidence": 0.95
                }
                """;

        notificationService.processAlarm(sandboxAlarm, "CRITICAL");

        // SECURITY: Sandbox 消息绝不推送
        verify(pushGateway, never()).pushCritical(any(), any(), any());
        verify(pushGateway, never()).pushWarning(any(), any(), any());
    }

    @Test
    @DisplayName("P0: processAlarm — is_sandbox=true 的 WARNING 消息绝不调用 pushGateway")
    void processAlarm_sandboxWarning_neverPushes() {
        String sandboxAlarm = """
                {
                  "alarm_id": "ALM-20260401-SBX-002",
                  "scene_id": "SCENE-SINTER-005",
                  "alarm_level": "WARNING",
                  "is_sandbox": true,
                  "confidence": 0.82
                }
                """;

        notificationService.processAlarm(sandboxAlarm, "WARNING");

        verify(pushGateway, never()).pushCritical(any(), any(), any());
        verify(pushGateway, never()).pushWarning(any(), any(), any());
    }

    @Test
    @DisplayName("P0: processAlarm — is_sandbox 字段缺失时，安全降级为 true，不推送")
    void processAlarm_missingSandboxField_defaultsTrueAndNeverPushes() {
        // is_sandbox 字段缺失 → 默认值 true（安全降级，规范：CLAUDE.md §11.1）
        String alarmWithoutSandboxField = """
                {
                  "alarm_id": "ALM-20260401-NOSANDBOX-001",
                  "scene_id": "SCENE-STEEL-001",
                  "alarm_level": "CRITICAL"
                }
                """;

        notificationService.processAlarm(alarmWithoutSandboxField, "CRITICAL");

        verify(pushGateway, never()).pushCritical(any(), any(), any());
        verify(pushGateway, never()).pushWarning(any(), any(), any());
    }

    // ── 生产告警推送行为 ──────────────────────────────────────

    @Test
    @DisplayName("processAlarm — 生产 CRITICAL 告警触发 pushCritical")
    void processAlarm_productionCritical_callsPushCritical() {
        String criticalAlarm = """
                {
                  "alarm_id": "ALM-20260401-001234",
                  "scene_id": "SCENE-SINTER-005",
                  "alarm_level": "CRITICAL",
                  "is_sandbox": false,
                  "confidence": 0.93,
                  "factory": "sintering"
                }
                """;

        notificationService.processAlarm(criticalAlarm, "CRITICAL");

        verify(pushGateway, times(1)).pushCritical(
                eq("ALM-20260401-001234"),
                eq("SCENE-SINTER-005"),
                any()
        );
        verify(pushGateway, never()).pushWarning(any(), any(), any());
    }

    @Test
    @DisplayName("processAlarm — 生产 WARNING 告警触发 pushWarning，不触发 pushCritical")
    void processAlarm_productionWarning_callsPushWarning() {
        String warningAlarm = """
                {
                  "alarm_id": "ALM-20260401-005678",
                  "scene_id": "SCENE-STRIP-003",
                  "alarm_level": "WARNING",
                  "is_sandbox": false,
                  "confidence": 0.83
                }
                """;

        notificationService.processAlarm(warningAlarm, "WARNING");

        verify(pushGateway, times(1)).pushWarning(
                eq("ALM-20260401-005678"),
                eq("SCENE-STRIP-003"),
                any()
        );
        verify(pushGateway, never()).pushCritical(any(), any(), any());
    }

    @Test
    @DisplayName("processAlarm — INFO 级别告警不触发任何推送（仅写数据库，由上游持久化）")
    void processAlarm_infoLevel_noPushGatewayCalled() {
        String infoAlarm = """
                {
                  "alarm_id": "ALM-20260401-009999",
                  "scene_id": "SCENE-PELLET-001",
                  "alarm_level": "INFO",
                  "is_sandbox": false,
                  "confidence": 0.71
                }
                """;

        notificationService.processAlarm(infoAlarm, "INFO");

        verify(pushGateway, never()).pushCritical(any(), any(), any());
        verify(pushGateway, never()).pushWarning(any(), any(), any());
    }

    @Test
    @DisplayName("processAlarm — JSON 解析异常时不抛出（异常被捕获，防止 Kafka offset 积压）")
    void processAlarm_invalidJson_doesNotThrow() {
        // 无效 JSON 不应导致 Kafka Consumer 崩溃
        assertThatCode(() -> notificationService.processAlarm("INVALID_JSON", "CRITICAL"))
                .doesNotThrowAnyException();

        verify(pushGateway, never()).pushCritical(any(), any(), any());
    }
}
