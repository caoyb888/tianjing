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

    private String sceneId;
    private String sceneName;
    private String factoryCode;
    private String processCode;
    private String category;
    private String priority;

    /** 场景状态：DRAFT / ACTIVE / INACTIVE / SANDBOX_ONLY */
    private String status;

    private String boundDeviceCode;
    @TableField("prod_model_id")
    private String activeModelVersionId;
    private String activePluginId;
    private Integer frameInterval;

    /** 告警配置（JSONB 存储，对应列 alarm_config_json）*/
    private String alarmConfigJson;

    /** 算法参数扩展（JSONB 存储，对应列 algo_config_json）*/
    @TableField("algo_config_json")
    private String algoParamsJson;

    /** 乐观锁版本号 */
    @Version
    private Integer version;

    @TableLogic
    private Boolean isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    private String createdBy;
    private String updatedBy;
}
