package com.tianzhu.tianjing.common.response;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * 链路追踪 ID 工具类
 * 从 MDC 获取当前请求的 trace_id，用于统一响应中的 trace_id 字段。
 * Micrometer Tracing 会自动将 traceId 写入 MDC。
 */
public final class TraceContext {

    private static final String MDC_TRACE_KEY = "traceId";

    private TraceContext() {}

    public static String currentTraceId() {
        String traceId = MDC.get(MDC_TRACE_KEY);
        return (traceId != null && !traceId.isBlank()) ? traceId : generateFallback();
    }

    private static String generateFallback() {
        // Jaeger 未接入时，临时生成 16 位 hex 作为占位符
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
