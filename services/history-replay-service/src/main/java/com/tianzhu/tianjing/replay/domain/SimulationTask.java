package com.tianzhu.tianjing.replay.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.List;

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

    @TableField(value = "workflow_json", typeHandler = com.tianzhu.tianjing.replay.config.JsonbTypeHandler.class)
    private String workflowJson;

    @TableField(value = "algo_config_json", typeHandler = com.tianzhu.tianjing.replay.config.JsonbTypeHandler.class)
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

    @TableField(value = "result_json", typeHandler = com.tianzhu.tianjing.replay.config.JsonbTypeHandler.class)
    private String resultJson;

    @TableField("error_msg")
    private String errorMsg;

    /** 仿真执行进度（0-100），每 10 帧更新一次 */
    @TableField("progress")
    private Integer progress;

    @TableField("started_at")
    private OffsetDateTime startedAt;

    /** DB列名 finished_at */
    @TableField("finished_at")
    private OffsetDateTime finishedAt;

    @TableLogic private Boolean isDeleted;

    @TableField(value = "created_at", fill = FieldFill.INSERT) private OffsetDateTime createdAt;
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE) private OffsetDateTime updatedAt;

    /** 导出的数据集版本 ID，导出成功后写入，用于溯源 */
    @TableField("dataset_version_id")
    private String datasetVersionId;

    /** 数据集导出状态：NULL / EXPORTING / EXPORTED / EXPORT_FAILED */
    @TableField("export_status")
    private String exportStatus;

    /** 导出结果摘要 JSON */
    @TableField("export_result_json")
    private String exportResultJson;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;

    @Version
    private Integer version;

    /** 关联视频列表（非 DB 字段，由 Service 层填充后返回） */
    @TableField(exist = false)
    private List<SimulationVideo> videos;
}
