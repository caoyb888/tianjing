package com.tianzhu.tianjing.algomodel.dto;

import java.time.OffsetDateTime;

/**
 * 算法插件可用性检测结果
 */
public record PluginHealthResult(
        boolean available,
        long    responseMs,
        String  message,
        String  backend,
        OffsetDateTime checkedAt
) {
    public static PluginHealthResult ok(long responseMs, String message, String backend) {
        return new PluginHealthResult(true,  responseMs, message, backend, OffsetDateTime.now());
    }

    public static PluginHealthResult fail(long responseMs, String message, String backend) {
        return new PluginHealthResult(false, responseMs, message, backend, OffsetDateTime.now());
    }
}
