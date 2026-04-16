package com.tianzhu.tianjing.auth.controller;

import com.tianzhu.tianjing.auth.dto.LoginRequest;
import com.tianzhu.tianjing.auth.dto.LoginResponse;
import com.tianzhu.tianjing.auth.dto.UserInfoResponse;
import com.tianzhu.tianjing.auth.service.AuthService;
import com.tianzhu.tianjing.common.response.ApiResponse;
import com.tianzhu.tianjing.common.security.TianjingUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 鉴权接口
 * 规范：API 接口规范 V3.1 §6.1
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * POST /auth/login — 用户登录
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ApiResponse.ok(response);
    }

    /**
     * GET /auth/me — 获取当前登录用户信息
     * 规范：API 接口规范 V3.1 §6.1
     */
    @GetMapping("/me")
    public ApiResponse<UserInfoResponse> me(
            @AuthenticationPrincipal TianjingUserDetails currentUser) {
        UserInfoResponse userInfo = authService.getCurrentUser(currentUser);
        return ApiResponse.ok(userInfo);
    }

    /**
     * POST /auth/refresh — 刷新 Token
     * 携带当前有效 Token 即可，无需请求体
     */
    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(
            @AuthenticationPrincipal TianjingUserDetails currentUser,
            HttpServletRequest request) {
        String token = extractToken(request);
        LoginResponse response = authService.refresh(currentUser, token);
        return ApiResponse.ok(response);
    }

    /**
     * POST /auth/logout — 退出登录
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> logout(HttpServletRequest request) {
        String token = extractToken(request);
        if (StringUtils.hasText(token)) {
            authService.logout(token);
        }
        return ApiResponse.ok();
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
