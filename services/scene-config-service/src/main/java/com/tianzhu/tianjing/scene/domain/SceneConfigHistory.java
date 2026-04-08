package com.tianzhu.tianjing.scene.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.tianzhu.tianjing.scene.config.JsonbTypeHandler;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 场景配置历史版本实体（对应 scene_config_history 表）
 * 每次 PUT 更新和 rollback 操作写入此表
 */
@Data
@TableName("scene_config_history")
public class SceneConfigHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("scene_id")
    private String sceneId;

    /** DB列名 config_version */
    @TableField("config_version")
    private Integer configVersion;

    /** 变更后的完整配置 JSON 快照，DB列名 snapshot_json */
    @TableField(value = "snapshot_json", typeHandler = JsonbTypeHandler.class)
    private String snapshotJson;

    /** 操作类型：UPDATE / ENABLE / DISABLE / ROLLBACK */
    @TableField("change_type")
    private String changeType;

    /** 操作人填写的变更说明 */
    @TableField("change_desc")
    private String changeDesc;

    /** DB列名 changed_by */
    @TableField("changed_by")
    private String changedBy;

    /** DB列名 changed_at */
    @TableField("changed_at")
    private OffsetDateTime changedAt;
}
