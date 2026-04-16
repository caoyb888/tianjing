package com.tianzhu.tianjing.scene.controller;

import com.tianzhu.tianjing.common.response.ApiResponse;
import com.tianzhu.tianjing.common.response.PageResult;
import com.tianzhu.tianjing.common.security.TianjingUserDetails;
import com.tianzhu.tianjing.scene.domain.SceneConfigHistory;
import com.tianzhu.tianjing.scene.dto.RollbackRequest;
import com.tianzhu.tianjing.scene.dto.SceneConfigDetail;
import com.tianzhu.tianjing.scene.dto.SceneConfigRequest;
import com.tianzhu.tianjing.scene.dto.WorkflowSaveRequest;
import com.tianzhu.tianjing.scene.service.SceneConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 场景配置接口
 * 规范：API 接口规范 V3.1 §6.2（8 个端点）
 */
@RestController
@RequestMapping("/api/v1/scenes")
@RequiredArgsConstructor
public class SceneConfigController {

    private final SceneConfigService sceneService;

    /**
     * GET /scenes — 查询场景列表（分页）
     */
    @GetMapping
    public ApiResponse<PageResult<SceneConfigDetail>> listScenes(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String factory,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String keyword) {

        return ApiResponse.page(sceneService.listScenes(page, size, factory, category, status, priority, keyword));
    }

    /**
     * POST /scenes — 新增场景（SCENE_EDITOR 或 ADMIN）
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SCENE_EDITOR', 'ADMIN')")
    public ApiResponse<SceneConfigDetail> createScene(
            @Valid @RequestBody SceneConfigRequest request,
            @AuthenticationPrincipal TianjingUserDetails user) {
        return ApiResponse.ok(sceneService.createScene(request, user.getUsername()));
    }

    /**
     * GET /scenes/{scene_id} — 查询单个场景
     */
    @GetMapping("/{scene_id}")
    public ApiResponse<SceneConfigDetail> getScene(@PathVariable("scene_id") String sceneId) {
        return ApiResponse.ok(sceneService.getScene(sceneId));
    }

    /**
     * PUT /scenes/{scene_id} — 更新场景配置（乐观锁）
     */
    @PutMapping("/{scene_id}")
    @PreAuthorize("hasAnyRole('SCENE_EDITOR', 'ADMIN')")
    public ApiResponse<SceneConfigDetail> updateScene(
            @PathVariable("scene_id") String sceneId,
            @Valid @RequestBody SceneConfigRequest request,
            @AuthenticationPrincipal TianjingUserDetails user) {
        return ApiResponse.ok(sceneService.updateScene(sceneId, request, user.getUsername()));
    }

    /**
     * DELETE /scenes/{scene_id} — 删除场景（软删除，ACTIVE 场景不可删除）
     */
    @DeleteMapping("/{scene_id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteScene(
            @PathVariable("scene_id") String sceneId,
            @AuthenticationPrincipal TianjingUserDetails user) {
        sceneService.deleteScene(sceneId, user.getUsername());
        return ApiResponse.ok();
    }

    /**
     * POST /scenes/{scene_id}/enable — 启用场景
     */
    @PostMapping("/{scene_id}/enable")
    @PreAuthorize("hasAnyRole('SCENE_EDITOR', 'ADMIN')")
    public ApiResponse<SceneConfigDetail> enableScene(
            @PathVariable("scene_id") String sceneId,
            @AuthenticationPrincipal TianjingUserDetails user) {
        return ApiResponse.ok(sceneService.enableScene(sceneId, user.getUsername()));
    }

    /**
     * POST /scenes/{scene_id}/disable — 禁用场景
     */
    @PostMapping("/{scene_id}/disable")
    @PreAuthorize("hasAnyRole('SCENE_EDITOR', 'ADMIN')")
    public ApiResponse<SceneConfigDetail> disableScene(
            @PathVariable("scene_id") String sceneId,
            @AuthenticationPrincipal TianjingUserDetails user) {
        return ApiResponse.ok(sceneService.disableScene(sceneId, user.getUsername()));
    }

    /**
     * PUT /scenes/{scene_id}/workflow — 保存算法编排工作流（独立端点）
     */
    @PutMapping("/{scene_id}/workflow")
    @PreAuthorize("hasAnyRole('SCENE_EDITOR', 'ADMIN')")
    public ApiResponse<SceneConfigDetail> saveWorkflow(
            @PathVariable("scene_id") String sceneId,
            @Valid @RequestBody WorkflowSaveRequest request,
            @AuthenticationPrincipal TianjingUserDetails user) {
        return ApiResponse.ok(sceneService.saveWorkflow(sceneId, request, user.getUsername()));
    }

    /**
     * GET /scenes/{scene_id}/history — 查询配置变更历史（接口 #11）
     */
    @GetMapping("/{scene_id}/history")
    public ApiResponse<PageResult<SceneConfigHistory>> getHistory(
            @PathVariable("scene_id") String sceneId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.page(sceneService.listHistory(sceneId, page, size));
    }

    /**
     * POST /scenes/{scene_id}/rollback — 回滚配置到指定历史版本
     */
    @PostMapping("/{scene_id}/rollback")
    @PreAuthorize("hasAnyRole('SCENE_EDITOR', 'ADMIN')")
    public ApiResponse<SceneConfigDetail> rollbackScene(
            @PathVariable("scene_id") String sceneId,
            @Valid @RequestBody RollbackRequest request,
            @AuthenticationPrincipal TianjingUserDetails user) {
        return ApiResponse.ok(sceneService.rollbackScene(sceneId, request, user.getUsername()));
    }
}
