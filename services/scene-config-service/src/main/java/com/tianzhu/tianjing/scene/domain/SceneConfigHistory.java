package com.tianzhu.tianjing.scene.domain;

import com.baomidou.mybatisplus.annotation.*;
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

    private String sceneId;
    private Integer historyVersion;
    private String configSnapshot;   // 变更后的完整配置 JSON 快照
    private String changeDiff;       // 变更内容摘要（human-readable）
    private String changeDesc;       // 操作人填写的变更说明
    private String changeType;       // UPDATE / ENABLE / DISABLE / ROLLBACK

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    private String createdBy;
}
