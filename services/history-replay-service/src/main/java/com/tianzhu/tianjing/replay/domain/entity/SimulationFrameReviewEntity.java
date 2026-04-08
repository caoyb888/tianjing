package com.tianzhu.tianjing.replay.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 仿真任务帧级标注审核记录实体
 * 对应表：simulation_frame_review
 * 规范：标注审核工具开发计划 V1.0
 */
@Data
@TableName("simulation_frame_review")
public class SimulationFrameReviewEntity {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联仿真任务ID */
    @TableField("task_id")
    private String taskId;

    /** 帧编号，如 frame_000001 */
    @TableField("frame_id")
    private String frameId;

    /** MinIO 帧图像地址 */
    @TableField("frame_url")
    private String frameUrl;

    /** 帧序号（0-based，用于分页排序） */
    @TableField("frame_index")
    private Integer frameIndex;

    /** 原始推理检测框JSON */
    @TableField("original_detections_json")
    private String originalDetectionsJson;

    /** 人工校正后检测框JSON（NULL表示未修改） */
    @TableField("corrected_detections_json")
    private String correctedDetectionsJson;

    /**
     * 审核状态
     * PENDING  = 未审核
     * APPROVED = 通过
     * REJECTED = 排除
     * SKIPPED  = 暂跳过
     */
    @TableField("review_status")
    private String reviewStatus;

    /** 是否人工修改过 */
    @TableField("is_modified")
    private Boolean isModified;

    /** 审核人 */
    @TableField("reviewed_by")
    private String reviewedBy;

    /** 审核时间 */
    @TableField("reviewed_at")
    private OffsetDateTime reviewedAt;

    /** 乐观锁版本号 */
    @Version
    @TableField("version")
    private Integer version;

    /** 软删除标记 */
    @TableLogic
    @TableField("is_deleted")
    private Boolean isDeleted;

    /** 创建时间 */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;
}
