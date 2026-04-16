package com.tianzhu.tianjing.lowcode.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

/**
 * 工作流节点（前端 Vue Flow 节点反序列化）
 *
 * 规范允许的 7 种节点类型（CLAUDE.md §4.2 lowcode-workflow-service）：
 * 1. FRAME_IN       — 帧输入节点（数据源）
 * 2. PREPROCESS     — 预处理节点（去雾/增强/裁剪）
 * 3. DETECT         — 目标检测节点
 * 4. SEGMENT        — 语义分割节点
 * 5. CLASSIFY       — 图像分类节点
 * 6. MEASURE        — 亚像素测量节点
 * 7. ALARM_OUT      — 告警输出节点（数据汇）
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WorkflowNode {

    @NotBlank(message = "节点 ID 不能为空")
    private String id;

    @NotBlank(message = "节点类型不能为空")
    private String type;

    /** 节点配置参数（各类型不同）*/
    private Map<String, Object> config;

    /** Vue Flow 画布位置（仅展示用，不影响执行逻辑）*/
    private Position position;

    @Data
    public static class Position {
        private double x;
        private double y;
    }
}
