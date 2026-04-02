package com.tianzhu.tianjing.scene.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 场景配置实体（对应 scene_config 表）
 * 规范：数据库设计文档 V2 §2.1
 */
@Data
@TableName("scene_config")
public class SceneConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("scene_id")
    private String sceneId;

    @TableField("scene_name")
    private String sceneName;

    @TableField("factory_code")
    private String factoryCode;

    @TableField("process_code")
    private String processCode;

    @TableField("category")
    private String category;

    @TableField("priority")
    private String priority;

    /** 场景状态：DRAFT / ACTIVE / INACTIVE / SANDBOX_ONLY */
    @TableField("status")
    private String status;

    /** 绑定的摄像头编码（运行时查询 camera_device，非数据库列） */
    @TableField(exist = false)
    private String boundDeviceCode;

    @TableField("prod_model_id")
    private String activeModelVersionId;

    @TableField("sandbox_model_id")
    private String sandboxModelId;

    /** 当前启用的算法插件（运行时关联，非数据库列） */
    @TableField(exist = false)
    private String activePluginId;

    @TableField("frame_interval")
    private Integer frameInterval;

    @TableField("sandbox_enabled")
    private Boolean sandboxEnabled;

    @TableField("workflow_json")
    private String workflowJson;

    /** 告警配置（JSONB 存储，对应列 alarm_config_json）*/
    @TableField("alarm_config_json")
    private String alarmConfigJson;

    /** 算法参数扩展（JSONB 存储，对应列 algo_config_json）*/
    @TableField("algo_config_json")
    private String algoParamsJson;

    @TableField("roi_config_json")
    private String roiConfigJson;

    /** 乐观锁版本号 */
    @Version
    private Integer version;

    @TableLogic
    private Boolean isDeleted;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;
}
