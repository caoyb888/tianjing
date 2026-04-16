package com.tianzhu.tianjing.alarm.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * SandboxInterceptor 单元测试
 *
 * 覆盖率要求：100%（规范：CLAUDE.md §12.1）
 * P0 安全红线：Sandbox 推理结果绝不触发外部推送（规范：CLAUDE.md §15）
 */
@DisplayName("SandboxInterceptor — P0 安全红线测试")
class SandboxInterceptorTest {

    private SandboxInterceptor sandboxInterceptor;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        sandboxInterceptor = new SandboxInterceptor(meterRegistry);
    }

    // ===== allowExternalPush() =====

    @Test
    @DisplayName("生产告警（isSandbox=false）— 允许外部推送")
    void allowExternalPush_production_returnsTrue() {
        boolean result = sandboxInterceptor.allowExternalPush(false);

        assertThat(result).isTrue();
        assertThat(interceptedCount()).isZero();
    }

    @Test
    @DisplayName("Sandbox 告警（isSandbox=true）— 拦截，禁止外部推送")
    void allowExternalPush_sandbox_returnsFalse() {
        boolean result = sandboxInterceptor.allowExternalPush(true);

        assertThat(result).isFalse();
        assertThat(interceptedCount()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("isSandbox=null — 视为不安全，默认拦截（防止字段丢失时误推）")
    void allowExternalPush_null_returnsFalse() {
        boolean result = sandboxInterceptor.allowExternalPush(null);

        assertThat(result).isFalse();
        assertThat(interceptedCount()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("多次 Sandbox 调用 — 计数器累加")
    void allowExternalPush_multipleSandboxCalls_counterAccumulates() {
        sandboxInterceptor.allowExternalPush(true);
        sandboxInterceptor.allowExternalPush(true);
        sandboxInterceptor.allowExternalPush(null);

        assertThat(interceptedCount()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("生产告警不增加拦截计数器")
    void allowExternalPush_production_doesNotIncrementCounter() {
        sandboxInterceptor.allowExternalPush(false);
        sandboxInterceptor.allowExternalPush(false);

        assertThat(interceptedCount()).isZero();
    }

    // ===== assertNotSandbox() =====

    @Test
    @DisplayName("assertNotSandbox — 生产告警不抛异常")
    void assertNotSandbox_production_noException() {
        assertThatNoException()
                .isThrownBy(() -> sandboxInterceptor.assertNotSandbox(false, "test-context"));
    }

    @Test
    @DisplayName("assertNotSandbox — Sandbox 告警抛出 SecurityException")
    void assertNotSandbox_sandbox_throwsSecurityException() {
        assertThatExceptionOfType(SecurityException.class)
                .isThrownBy(() -> sandboxInterceptor.assertNotSandbox(true, "alarm-push"))
                .withMessageContaining("alarm-push");
    }

    @Test
    @DisplayName("assertNotSandbox — null isSandbox 抛出 SecurityException")
    void assertNotSandbox_null_throwsSecurityException() {
        assertThatExceptionOfType(SecurityException.class)
                .isThrownBy(() -> sandboxInterceptor.assertNotSandbox(null, "re-push"))
                .withMessageContaining("re-push");
    }

    @Test
    @DisplayName("assertNotSandbox — Sandbox 时计数器仍然递增")
    void assertNotSandbox_sandbox_counterIncremented() {
        try {
            sandboxInterceptor.assertNotSandbox(true, "ctx");
        } catch (SecurityException ignored) {}

        assertThat(interceptedCount()).isEqualTo(1.0);
    }

    // ===== 核心场景：模拟 alarm-judge-service 完整拦截流程 =====

    @Test
    @DisplayName("核心 P0：Sandbox 推理结果绝不触发告警推送")
    void coreScenario_sandboxResultNeverTriggersPush() {
        // 模拟 alarm-judge-service.AlarmService.process() 中的拦截逻辑
        Boolean isSandbox = true;
        boolean notificationSent = false;

        if (sandboxInterceptor.allowExternalPush(isSandbox)) {
            notificationSent = true; // 生产代码中此处调用推送服务
        }

        assertThat(notificationSent).isFalse();
        assertThat(interceptedCount()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("核心 P0：生产告警正常通过拦截器")
    void coreScenario_productionAlarmPassesThroughInterceptor() {
        Boolean isSandbox = false;
        boolean notificationSent = false;

        if (sandboxInterceptor.allowExternalPush(isSandbox)) {
            notificationSent = true;
        }

        assertThat(notificationSent).isTrue();
        assertThat(interceptedCount()).isZero();
    }

    @Test
    @DisplayName("核心 P0：is_sandbox 字段丢失（null）时安全降级拦截")
    void coreScenario_missingIsSandboxFieldSafelyBlocked() {
        // 模拟消息链路中 is_sandbox 字段未传递的情况
        Boolean isSandbox = null;
        boolean notificationSent = false;

        if (sandboxInterceptor.allowExternalPush(isSandbox)) {
            notificationSent = true;
        }

        assertThat(notificationSent).isFalse();
        assertThat(interceptedCount()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Prometheus 指标名称正确注册")
    void prometheusMetric_registeredWithCorrectName() {
        // 触发一次拦截以确保计数器被注册
        sandboxInterceptor.allowExternalPush(true);

        Counter counter = meterRegistry.find("tianjing_alarm_intercepted_total").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    private double interceptedCount() {
        Counter counter = meterRegistry.find("tianjing_alarm_intercepted_total").counter();
        return counter != null ? counter.count() : 0.0;
    }
}
