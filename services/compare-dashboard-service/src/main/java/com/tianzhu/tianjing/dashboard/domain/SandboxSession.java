package com.tianzhu.tianjing.dashboard.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

/**
 * Sandbox 推理会话（对应 sandbox_infer_session 表）
 * 规范：数据库设计文档 V2，CLAUDE.md §11
 */
@Data
@TableName("sandbox_infer_session")
public class SandboxSession {
    @TableId(type = IdType.AUTO) private Long id;

    @TableField("session_id")
    private String sessionId;

    @TableField("scene_id")
    private String sceneId;

    /** 对照的生产模型版本，DB列名 prod_model_id */
    @TableField("prod_model_id")
    private String prodModelId;

    /** 实验候选模型版本，DB列名 candidate_model_id */
    @TableField("candidate_model_id")
    private String candidateModelId;

    @TableField("mirror_fps")
    private Integer mirrorFps;

    /** 状态：RUNNING / STOPPED / COMPLETED */
    @TableField("status")
    private String status;

    /** DB列名 start_at */
    @TableField("start_at")
    private OffsetDateTime startAt;

    /** DB列名 end_at */
    @TableField("end_at")
    private OffsetDateTime endAt;

    @TableField("total_frames")
    private Integer totalFrames;

    @TableField("total_anomaly_frames")
    private Integer totalAnomalyFrames;

    @TableField("created_by")
    private String createdBy;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
