package com.tianzhu.tianjing.dashboard.controller;

import com.tianzhu.tianjing.common.response.ApiResponse;
import com.tianzhu.tianjing.common.response.PageResult;
import com.tianzhu.tianjing.common.security.TianjingUserDetails;
import com.tianzhu.tianjing.dashboard.domain.SandboxCompareReport;
import com.tianzhu.tianjing.dashboard.domain.SandboxSession;
import com.tianzhu.tianjing.dashboard.dto.SandboxPromoteRequest;
import com.tianzhu.tianjing.dashboard.dto.SandboxStartRequest;
import com.tianzhu.tianjing.dashboard.service.SandboxService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Sandbox 实验室接口
 * 规范：API 接口规范 V3.1 §6.8（5 个端点）
 */
@RestController
@RequestMapping("/api/v1/sandbox")
@RequiredArgsConstructor
public class SandboxController {

    private final SandboxService sandboxService;

    @GetMapping("/sessions")
    public ApiResponse<PageResult<SandboxSession>> listSessions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String scene_id) {
        return ApiResponse.page(sandboxService.listSessions(page, size, scene_id));
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SANDBOX_OPERATOR', 'ADMIN')")
    public ApiResponse<SandboxSession> startSession(
            @Valid @RequestBody SandboxStartRequest request,
            @AuthenticationPrincipal TianjingUserDetails user) {
        return ApiResponse.ok(sandboxService.startSession(request, user.getUsername()));
    }

    /** GET /sandbox/sessions/{session_id} — 会话详情（接口 #35） */
    @GetMapping("/sessions/{session_id}")
    public ApiResponse<SandboxSession> getSession(
            @PathVariable("session_id") String sessionId) {
        return ApiResponse.ok(sandboxService.getSession(sessionId));
    }

    @PostMapping("/sessions/{session_id}/stop")
    @PreAuthorize("hasAnyRole('SANDBOX_OPERATOR', 'ADMIN')")
    public ApiResponse<SandboxSession> stopSession(
            @PathVariable("session_id") String sessionId,
            @AuthenticationPrincipal TianjingUserDetails user) {
        return ApiResponse.ok(sandboxService.stopSession(sessionId, user.getUsername()));
    }

    @GetMapping("/sessions/{session_id}/report")
    public ApiResponse<SandboxCompareReport> getReport(
            @PathVariable("session_id") String sessionId) {
        return ApiResponse.ok(sandboxService.getCompareReport(sessionId));
    }

    /**
     * POST /sandbox/sessions/{session_id}/promote — Sandbox 转正
     * 门禁：48h / 精度+2% / 资源 / 延迟 / 2人审核
     */
    @PostMapping("/sessions/{session_id}/promote")
    @PreAuthorize("hasAnyRole('SANDBOX_OPERATOR', 'ADMIN')")
    public ApiResponse<Map<String, Object>> promote(
            @PathVariable("session_id") String sessionId,
            @RequestBody(required = false) SandboxPromoteRequest request,
            @AuthenticationPrincipal TianjingUserDetails user) {
        return ApiResponse.ok(sandboxService.promoteSession(sessionId,
                request != null ? request : new SandboxPromoteRequest(null), user.getUsername()));
    }
}
