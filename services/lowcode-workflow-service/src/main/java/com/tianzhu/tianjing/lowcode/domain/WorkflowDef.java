package com.tianzhu.tianjing.lowcode.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 工作流定义（低代码编排器后端存储）
 *
 * 对应表：workflow_def（tianjing_prod 库）
 * 规范：CLAUDE.md §5.1、§7.1
 */
@Data
@TableName("workflow_def")
public class WorkflowDef {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工作流唯一标识，格式：WF-{sceneId}-{序号} */
    @TableField("workflow_id")
    private String workflowId;

    /** 关联场景 ID */
    @TableField("scene_id")
    private String sceneId;

    /** 工作流名称 */
    @TableField("workflow_name")
    private String workflowName;

    /** 工作流状态：DRAFT | PUBLISHED | ARCHIVED */
    @TableField("status")
    private String status;

    /**
     * 工作流 JSON（由前端 Vue Flow 画布生成，后端校验节点类型合法性）
     * 节点类型限制：FRAME_IN / PREPROCESS / DETECT / SEGMENT / CLASSIFY / MEASURE / ALARM_OUT
     */
    @TableField("workflow_json")
    private String workflowJson;

    /** 版本号（乐观锁）*/
    @Version
    @TableField("version")
    private Integer version;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    @TableField("created_by")
    private String createdBy;

    @TableLogic
    @TableField("is_deleted")
    private Integer isDeleted;
}
