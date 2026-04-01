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
    private String taskId;
    private String sceneId;
    private String videoFileName;
    /** MinIO 对象路径 */
    private String videoObjectPath;
    /** 状态：PENDING / RUNNING / COMPLETED / FAILED */
    private String status;
    /** 仿真进度（0-100） */
    private Integer progressPercent;
    /** 仿真结果摘要 JSON */
    private String resultSummaryJson;
    /** 上传时间（72h 过期基准） */
    private OffsetDateTime uploadedAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    @TableLogic private Boolean isDeleted;
    @TableField(fill = FieldFill.INSERT) private OffsetDateTime createdAt;
    private String createdBy;
}
