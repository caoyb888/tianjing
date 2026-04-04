package com.tianzhu.tianjing.scene.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 保存算法编排工作流请求体
 * PUT /api/v1/scenes/{scene_id}/workflow
 */
public record WorkflowSaveRequest(
        @NotNull(message = "workflowJson 不能为空")
        Object workflowJson
) {}
