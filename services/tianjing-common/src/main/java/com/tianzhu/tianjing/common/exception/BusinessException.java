package com.tianzhu.tianjing.common.exception;

import lombok.Getter;

/**
 * 业务异常基类
 * 由 GlobalExceptionHandler 捕获后转换为统一 ApiResponse 格式返回。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String detail;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.detail = null;
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.detail = detail;
    }

    public BusinessException(ErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.detail = detail;
    }

    // ==============================
    // 常用工厂方法（减少 new 调用的样板代码）
    // ==============================

    public static BusinessException of(ErrorCode code) {
        return new BusinessException(code);
    }

    public static BusinessException of(ErrorCode code, String detail) {
        return new BusinessException(code, detail);
    }

    public static BusinessException notFound(ErrorCode code) {
        return new BusinessException(code);
    }

    public static BusinessException conflict(String detail) {
        return new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT, detail);
    }

    public static BusinessException forbidden(ErrorCode code) {
        return new BusinessException(code);
    }
}
