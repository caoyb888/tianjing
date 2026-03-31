package com.tianzhu.tianjing.alarm.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Sandbox 拦截器
 *
 * P0 安全红线：Sandbox 推理结果绝不触发 MQTT 告警推送或写入工业互联网平台
 * 规范：CLAUDE.md §11.1，§15 P0 禁止行为清单
 *
 * 此拦截器是整个 alarm-judge-service 的最高优先级校验，
 * 所有外部推送操作前必须通过此拦截器的 check() 方法。
 *
 * 测试覆盖率要求：100%（规范：CLAUDE.md §12.1）
 */
@Slf4j
@Component
public class SandboxInterceptor {

    private final Counter sandboxInterceptedCounter;

    public SandboxInterceptor(MeterRegistry meterRegistry) {
        // Prometheus 指标：tianjing_alarm_intercepted_total（规范：CLAUDE.md §14.2）
        this.sandboxInterceptedCounter = Counter.builder("tianjing_alarm_intercepted_total")
                .description("Sandbox 拦截的告警总次数")
                .register(meterRegistry);
    }

    /**
     * SECURITY: 核心拦截逻辑
     * 在任何外部推送（MQTT / 企业微信 / 工单）前必须调用此方法。
     *
     * @param isSandbox 是否来自 Sandbox 流
     * @return true = 允许推送；false = 拦截，禁止推送
     * @throws IllegalArgumentException 如果 isSandbox 为 null（防止空指针导致拦截失效）
     */
    public boolean allowExternalPush(Boolean isSandbox) {
        // SECURITY: null 视为不安全状态，默认拦截（防止 is_sandbox 字段丢失时误推）
        if (isSandbox == null) {
            log.error("SECURITY ALERT: is_sandbox 字段为 null，默认拦截！请检查消息链路是否丢失 is_sandbox 标识");
            sandboxInterceptedCounter.increment();
            return false;
        }

        if (isSandbox) {
            log.info("SECURITY: Sandbox 告警已拦截，不触发外部推送");
            sandboxInterceptedCounter.increment();
            return false;
        }

        return true;
    }

    /**
     * 便捷方法：断言不是 Sandbox 消息，否则抛出异常（用于方法前置守卫）
     */
    public void assertNotSandbox(Boolean isSandbox, String context) {
        if (!allowExternalPush(isSandbox)) {
            throw new SecurityException("Sandbox 拦截器阻止了外部推送操作: " + context);
        }
    }
}
