package com.tianzhu.tianjing.lowcode.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 工作流连接边（Vue Flow Edge）
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowEdge {
    private String id;
    private String source;
    private String target;
    private String sourceHandle;
    private String targetHandle;
}
