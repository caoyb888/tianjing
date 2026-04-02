package com.tianzhu.tianjing.replay.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

/**
 * 离线仿真任务（对应 simulation_task 表）
 * 规范：API 接口规范 V3.1 §6.9
 * 视频文件 72h 自动过期，过期返回 2009
 */
@Data
@TableName("simulation_task")
public class SimulationTask {
    @TableId(type = IdType.AUTO) private Long id;

    @TableField("task_id")
    private String taskId;

    @TableField("scene_id")
    private String sceneId;

    /** DB列名 task_name */
    @TableField("task_name")
    private String taskName;

    /** 视频文件 URL（MinIO），DB列名 video_file_url */
    @TableField("video_file_url")
    private String videoFileUrl;

    @TableField("video_duration_s")
    private Integer videoDurationS;

    @TableField("video_fps")
    private Integer videoFps;

    @TableField("workflow_json")
    private String workflowJson;

    @TableField("algo_config_json")
    private String algoConfigJson;

    /** 状态：PENDING / RUNNING / COMPLETED / FAILED */
    @TableField("status")
    private String status;

    @TableField("total_frames")
    private Integer totalFrames;

    @TableField("matched_alarms")
    private Integer matchedAlarms;

    @TableField("false_alarm_count")
    private Integer falseAlarmCount;

    /** 仿真结果摘要，DB列名 result_summary */
    @TableField("result_summary")
    private String resultSummary;

    @TableField("result_json")
    private String resultJson;

    @TableField("error_msg")
    private String errorMsg;

    @TableField("started_at")
    private OffsetDateTime startedAt;

    /** DB列名 finished_at */
    @TableField("finished_at")
    private OffsetDateTime finishedAt;

    @TableLogic private Boolean isDeleted;

    @TableField(value = "created_at", fill = FieldFill.INSERT) private OffsetDateTime createdAt;
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE) private OffsetDateTime updatedAt;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;

    @Version
    private Integer version;
}
