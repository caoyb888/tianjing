package com.tianzhu.tianjing.common.web;

import com.tianzhu.tianjing.common.exception.BusinessException;
import com.tianzhu.tianjing.common.exception.ErrorCode;
import com.tianzhu.tianjing.common.response.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 将所有异常转换为标准 ApiResponse 格式，HTTP 状态码映射见 §4.1
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==============================
    // 业务异常（已知异常，精确映射 HTTP 状态码）
    // ==============================

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.getErrorCode();
        HttpStatus httpStatus = mapHttpStatus(code);
        log.warn("业务异常 [{}] {}: {}", code.getCode(), code.getMessage(), ex.getDetail());
        return ResponseEntity.status(httpStatus)
                .body(ApiResponse.error(code, ex.getDetail()));
    }

    // ==============================
    // 参数校验异常（@Valid / @Validated）
    // ==============================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.debug("参数校验失败: {}", detail);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.PARAM_MISSING, detail));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.debug("约束校验失败: {}", detail);
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.PARAM_MISSING, detail));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.PARAM_MISSING, "缺少必要参数: " + ex.getParameterName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.PARAM_ENUM_INVALID,
                        "参数类型不匹配: " + ex.getName()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.PARAM_JSON_INVALID));
    }

    // ==============================
    // Spring Security 异常
    // ==============================

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuth(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ErrorCode.TOKEN_MISSING_OR_EXPIRED));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ErrorCode.PERMISSION_DENIED));
    }

    // ==============================
    // 兜底异常（未知系统异常）
    // ==============================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception ex) {
        log.error("未预期的系统异常", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR));
    }

    // ==============================
    // 错误码 → HTTP 状态码映射
    // ==============================

    private HttpStatus mapHttpStatus(ErrorCode code) {
        int c = code.getCode();
        if (c == 2001) return HttpStatus.CONFLICT;        // 乐观锁冲突 → 409
        if (c >= 1000 && c < 3000) return HttpStatus.BAD_REQUEST;   // 1xxx/2xxx → 400
        if (c >= 3000 && c < 4000) return HttpStatus.NOT_FOUND;      // 3xxx → 404
        if (c == 4001 || c == 4002) return HttpStatus.UNAUTHORIZED;  // 未认证 → 401
        if (c >= 4000 && c < 5000) return HttpStatus.FORBIDDEN;      // 4xxx → 403
        return HttpStatus.INTERNAL_SERVER_ERROR;                      // 5xxx → 500
    }
}
