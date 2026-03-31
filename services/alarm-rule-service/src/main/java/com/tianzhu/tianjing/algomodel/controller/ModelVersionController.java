package com.tianzhu.tianjing.algomodel.controller;

import com.tianzhu.tianjing.algomodel.domain.ModelVersion;
import com.tianzhu.tianjing.algomodel.dto.ModelApproveRequest;
import com.tianzhu.tianjing.algomodel.dto.ModelRegisterRequest;
import com.tianzhu.tianjing.algomodel.service.ModelVersionService;
import com.tianzhu.tianjing.common.response.ApiResponse;
import com.tianzhu.tianjing.common.response.PageResult;
import com.tianzhu.tianjing.common.security.TianjingUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 模型版本管理接口
 * 规范：API 接口规范 V3.1 §6.6（5 个端点）
 * 状态机：STAGING → SANDBOX_VALIDATING → REVIEWING → PRODUCTION → DEPRECATED
 */
@RestController
@RequestMapping("/api/v1/models")
@RequiredArgsConstructor
public class ModelVersionController {

    private final ModelVersionService modelVersionService;

    @GetMapping
    public ApiResponse<PageResult<ModelVersion>> listVersions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String plugin_id,
            @RequestParam(required = false) String status) {
        return ApiResponse.page(modelVersionService.listVersions(page, size, plugin_id, status));
    }

    @GetMapping("/{model_version_id}")
    public ApiResponse<ModelVersion> getVersion(
            @PathVariable("model_version_id") String modelVersionId) {
        return ApiResponse.ok(modelVersionService.getVersion(modelVersionId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'MODEL_REVIEWER')")
    public ApiResponse<ModelVersion> registerVersion(
            @Valid @RequestBody ModelRegisterRequest request,
            @AuthenticationPrincipal TianjingUserDetails user) {
        return ApiResponse.ok(modelVersionService.registerVersion(request, user.getUsername()));
    }

    /**
     * POST /models/{id}/approve — 审核（四眼原则：审核人 ≠ 提交人）
     */
    @PostMapping("/{model_version_id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODEL_REVIEWER')")
    public ApiResponse<ModelVersion> approve(
            @PathVariable("model_version_id") String modelVersionId,
            @Valid @RequestBody ModelApproveRequest request,
            @AuthenticationPrincipal TianjingUserDetails user) {
        return ApiResponse.ok(modelVersionService.approve(modelVersionId, request, user.getUsername()));
    }

    @PostMapping("/{model_version_id}/deprecate")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deprecate(
            @PathVariable("model_version_id") String modelVersionId,
            @AuthenticationPrincipal TianjingUserDetails user) {
        modelVersionService.deprecate(modelVersionId, user.getUsername());
        return ApiResponse.ok();
    }
}
