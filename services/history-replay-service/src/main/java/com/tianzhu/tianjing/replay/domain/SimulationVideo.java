package com.tianzhu.tianjing.replay.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 仿真视频文件（simulation_video 表）
 * 一个仿真任务可包含多个视频，支持 NORMAL / ABNORMAL / MIXED 标注
 */
@Data
@TableName("simulation_video")
public class SimulationVideo {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("video_id")
    private String videoId;

    @TableField("task_id")
    private String taskId;

    @TableField("video_url")
    private String videoUrl;

    @TableField("video_name")
    private String videoName;

    /** NORMAL=正常录像 / ABNORMAL=含缺陷录像 / MIXED=混合 */
    @TableField("label")
    private String label;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("duration_s")
    private Integer durationS;

    /** PENDING / RUNNING / COMPLETED / FAILED */
    @TableField("status")
    private String status;

    @TableField("total_frames")
    private Integer totalFrames;

    @TableField("matched_alarms")
    private Integer matchedAlarms;

    @TableField("error_msg")
    private String errorMsg;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField("created_by")
    private String createdBy;
}
