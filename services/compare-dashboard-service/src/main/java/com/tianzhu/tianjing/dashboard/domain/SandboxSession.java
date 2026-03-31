package com.tianzhu.tianjing.dashboard.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

/**
 * Sandbox 推理会话（对应 sandbox_infer_session 表）
 * 规范：数据库设计文档 V2，CLAUDE.md §11
 */
@Data
@TableName(value = "sandbox_infer_session", schema = "tianjing_sandbox")
public class SandboxSession {
    @TableId(type = IdType.AUTO) private Long id;
    private String sessionId;
    private String sceneId;
    /** 对照的生产模型版本 */
    private String productionModelVersionId;
    /** 实验模型版本 */
    private String experimentModelVersionId;
    /** 状态：RUNNING / STOPPED / COMPLETED */
    private String status;
    private OffsetDateTime startedAt;
    private OffsetDateTime stoppedAt;
    /** 已运行小时数（精度评估用） */
    private Double runningHours;
    private String createdBy;
    @TableField(fill = FieldFill.INSERT) private OffsetDateTime createdAt;
}
