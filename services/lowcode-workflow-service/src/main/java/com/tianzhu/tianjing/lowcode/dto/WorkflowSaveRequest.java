package com.tianzhu.tianjing.lowcode.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 工作流保存/发布请求
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowSaveRequest {

    @NotBlank(message = "场景 ID 不能为空")
    private String sceneId;

    @NotBlank(message = "工作流名称不能为空")
    private String workflowName;

    @NotEmpty(message = "工作流节点列表不能为空")
    @Valid
    private List<WorkflowNode> nodes;

    /** 节点连接边列表 */
    private List<WorkflowEdge> edges;

    /** 乐观锁版本号（更新时必填） */
    private Integer version;
}
