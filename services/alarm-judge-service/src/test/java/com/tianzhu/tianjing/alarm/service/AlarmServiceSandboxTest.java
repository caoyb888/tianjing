package com.tianzhu.tianjing.alarm.service;

import com.tianzhu.tianjing.alarm.domain.AlarmRecord;
import com.tianzhu.tianjing.alarm.dto.AlarmFeedbackRequest;
import com.tianzhu.tianjing.alarm.repository.AlarmRecordMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AlarmService Sandbox 拦截行为集成单元测试
 *
 * 规范：CLAUDE.md §12.2 必须包含的测试用例
 *   - test_sandbox_alarm_intercepted: Sandbox 推理结果绝不触发告警推送
 *   - test_production_critical_alarm_sent: 生产 CRITICAL 告警正常触发多通道推送
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AlarmService — Sandbox 拦截行为测试（CLAUDE.md §12.2）")
class AlarmServiceSandboxTest {

    @Mock
    private AlarmRecordMapper alarmMapper;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private SandboxInterceptor sandboxInterceptor;
    private AlarmService alarmService;

    @BeforeEach
    void setUp() {
        sandboxInterceptor = new SandboxInterceptor(new SimpleMeterRegistry());
        alarmService = new AlarmService(alarmMapper, sandboxInterceptor, kafkaTemplate);
    }

    // ===== CLAUDE.md §12.2 必须包含的测试用例 =====

    @Test
    @DisplayName("§12.2 核心：Sandbox 推理结果绝不触发告警推送")
    void test_sandbox_alarm_intercepted() {
        // Sandbox 告警记录（is_sandbox=true, status=FAILED 触发 retryPush）
        AlarmRecord sandboxAlarm = buildAlarmRecord("ALM-SANDBOX-001", true, "FAILED");
        when(alarmMapper.selectOne(any())).thenReturn(sandboxAlarm);

        // 执行重推（retryPush 内部必须经过 SandboxInterceptor 校验）
        assertThatExceptionOfType(SecurityException.class)
                .isThrownBy(() -> alarmService.retryPush("ALM-SANDBOX-001"))
                .withMessageContaining("Sandbox 拦截器阻止了外部推送操作");

        // 验证：Kafka 推送未被调用（Sandbox 消息绝不到达 notification-service）
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("§12.2 核心：生产 CRITICAL 告警正常触发多通道推送")
    void test_production_critical_alarm_sent() {
        // 生产 CRITICAL 告警记录（is_sandbox=false, status=FAILED）
        AlarmRecord productionAlarm = buildAlarmRecord("ALM-PROD-001", false, "FAILED");
        when(alarmMapper.selectOne(any())).thenReturn(productionAlarm);
        when(alarmMapper.updateById(any(AlarmRecord.class))).thenReturn(1);

        // 执行重推
        alarmService.retryPush("ALM-PROD-001");

        // 验证：消息发布至 CRITICAL topic（notification-service 将推送告警）
        verify(kafkaTemplate, times(1)).send(
                eq("tianjing.alarm.critical"),
                eq("SCENE-SINTER-005"),
                anyString()
        );
    }

    @Test
    @DisplayName("Sandbox 告警反馈 — 写入 drift.feedback Topic（不影响生产推送）")
    void sandbox_alarm_feedback_writesToDriftTopic() {
        AlarmRecord sandboxAlarm = buildAlarmRecord("ALM-SBX-002", true, "PUSHED");
        when(alarmMapper.selectOne(any())).thenReturn(sandboxAlarm);
        when(alarmMapper.updateById(any(AlarmRecord.class))).thenReturn(1);

        AlarmFeedbackRequest feedbackRequest = new AlarmFeedbackRequest("FALSE_POSITIVE", "误报，现场无异常");
        alarmService.submitFeedback("ALM-SBX-002", feedbackRequest, "operator1");

        // 反馈写入漂移监测 Topic（规范：CLAUDE.md §8.1 tianjing.drift.feedback）
        verify(kafkaTemplate, times(1)).send(
                eq("tianjing.drift.feedback"),
                eq("SCENE-SINTER-005"),
                anyString()
        );
    }

    @Test
    @DisplayName("非 FAILED 状态告警不允许重推")
    void retryPush_nonFailedStatus_throwsBusinessException() {
        AlarmRecord alarm = buildAlarmRecord("ALM-PROD-002", false, "PUSHED");
        when(alarmMapper.selectOne(any())).thenReturn(alarm);

        assertThatException()
                .isThrownBy(() -> alarmService.retryPush("ALM-PROD-002"));

        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("is_sandbox=null 重推 — SandboxInterceptor 安全拦截")
    void retryPush_nullIsSandbox_blocked() {
        AlarmRecord alarm = buildAlarmRecord("ALM-NULL-001", null, "FAILED");
        when(alarmMapper.selectOne(any())).thenReturn(alarm);

        // null is_sandbox 视为不安全，必须拦截（规范：CLAUDE.md §11.1）
        assertThatExceptionOfType(SecurityException.class)
                .isThrownBy(() -> alarmService.retryPush("ALM-NULL-001"));

        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    // ===== 工厂方法 =====

    private AlarmRecord buildAlarmRecord(String alarmId, Boolean isSandbox, String pushStatus) {
        AlarmRecord alarm = new AlarmRecord();
        alarm.setAlarmId(alarmId);
        alarm.setSceneId("SCENE-SINTER-005");
        alarm.setFactoryCode("SINTER");
        alarm.setAlarmLevel("CRITICAL");
        alarm.setIsSandbox(isSandbox);
        alarm.setPushStatus(pushStatus);
        alarm.setAlarmAt(java.time.OffsetDateTime.now());
        return alarm;
    }
}
