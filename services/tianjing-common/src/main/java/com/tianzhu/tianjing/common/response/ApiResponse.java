package com.tianzhu.tianjing.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tianzhu.tianjing.common.exception.ErrorCode;
import lombok.Getter;

/**
 * 统一 API 响应包装体
 * 规范：API 接口规范 V3.1 §3
 * 所有接口必须使用此结构返回，禁止自定义格式。
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final int code;
    private final String message;
    private final T data;
    private final String traceId;
    private final String detail;

    private ApiResponse(int code, String message, T data, String traceId, String detail) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
        this.detail = detail;
    }

    // ==============================
    // 成功响应
    // ==============================

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "success", data, TraceContext.currentTraceId(), null);
    }

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(0, "success", null, TraceContext.currentTraceId(), null);
    }

    // ==============================
    // 错误响应
    // ==============================

    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.getCode(), errorCode.getMessage(),
                null, TraceContext.currentTraceId(), null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String detail) {
        return new ApiResponse<>(errorCode.getCode(), errorCode.getMessage(),
                null, TraceContext.currentTraceId(), detail);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, TraceContext.currentTraceId(), null);
    }

    public static <T> ApiResponse<T> error(int code, String message, String detail) {
        return new ApiResponse<>(code, message, null, TraceContext.currentTraceId(), detail);
    }

    // ==============================
    // 分页响应工厂方法
    // ==============================

    public static <T> ApiResponse<PageResult<T>> page(PageResult<T> pageResult) {
        return new ApiResponse<>(0, "success", pageResult, TraceContext.currentTraceId(), null);
    }
}
