package com.tianzhu.tianjing.lowcode.controller;

import com.tianzhu.tianjing.common.response.ApiResponse;
import com.tianzhu.tianjing.common.response.PageResult;
import com.tianzhu.tianjing.lowcode.domain.WorkflowDef;
import com.tianzhu.tianjing.lowcode.dto.WorkflowSaveRequest;
import com.tianzhu.tianjing.lowcode.service.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 低代码工作流接口（S1-21）
 *
 * 规范：CLAUDE.md §9.1 REST API 规范
 * 操作权限：需要 SCENE_EDITOR 角色（规范：CLAUDE.md §10.3）
 */
@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    /**
     * 查询场景工作流列表
     * GET /api/v1/workflows?scene_id=SCENE-SINTER-005&page=1&size=20
     */
    @GetMapping
    public ApiResponse<PageResult<WorkflowDef>> list(
            @RequestParam(required = false) String sceneId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.page(workflowService.listByScene(sceneId, page, size));
    }

    /**
     * 新建工作流（草稿）
     * POST /api/v1/workflows
     */
    @PostMapping
    @PreAuthorize("hasRole('SCENE_EDITOR')")
    public ApiResponse<WorkflowDef> create(@Valid @RequestBody WorkflowSaveRequest request) {
        return ApiResponse.ok(workflowService.saveWorkflow(request));
    }

    /**
     * 获取工作流详情
     * GET /api/v1/workflows/{workflowId}
     */
    @GetMapping("/{workflowId}")
    public ApiResponse<WorkflowDef> get(@PathVariable String workflowId) {
        return ApiResponse.ok(workflowService.getWorkflow(workflowId));
    }

    /**
     * 更新工作流
     * PUT /api/v1/workflows/{workflowId}
     */
    @PutMapping("/{workflowId}")
    @PreAuthorize("hasRole('SCENE_EDITOR')")
    public ApiResponse<WorkflowDef> update(@PathVariable String workflowId,
                                           @Valid @RequestBody WorkflowSaveRequest request) {
        return ApiResponse.ok(workflowService.updateWorkflow(workflowId, request));
    }

    /**
     * 发布工作流（DRAFT → PUBLISHED，同步至配置中心）
     * POST /api/v1/workflows/{workflowId}/publish
     */
    @PostMapping("/{workflowId}/publish")
    @PreAuthorize("hasRole('SCENE_EDITOR')")
    public ApiResponse<WorkflowDef> publish(@PathVariable String workflowId) {
        return ApiResponse.ok(workflowService.publishWorkflow(workflowId));
    }

    /**
     * 删除工作流（仅 DRAFT 状态可删除）
     * DELETE /api/v1/workflows/{workflowId}
     */
    @DeleteMapping("/{workflowId}")
    @PreAuthorize("hasRole('SCENE_EDITOR')")
    public ApiResponse<Void> delete(@PathVariable String workflowId) {
        workflowService.deleteWorkflow(workflowId);
        return ApiResponse.ok();
    }
}
