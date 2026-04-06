package com.tianzhu.tianjing.algomodel.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

/**
 * 算法插件实体（对应 algorithm_plugin 表）
 */
@Data
@TableName("algorithm_plugin")
public class AlgorithmPlugin {
    @TableId(type = IdType.AUTO) private Long id;

    @TableField("plugin_id")
    private String pluginId;

    @TableField("plugin_name")
    private String pluginName;

    @TableField("version")
    private String version;

    @TableField("plugin_type")
    private String pluginType;

    @TableField("is_atom")
    private Boolean isAtom;

    @TableField("parent_plugin_id")
    private String parentPluginId;

    @TableField("backbone")
    private String backbone;

    /** DB 中以 metadata_json 存储，以下字段在实体层不映射 */
    @TableField(exist = false) private String supportedScenes;
    @TableField(exist = false) private String hardwareRequirementsJson;
    @TableField(exist = false) private String accuracyMetricsJson;

    @TableField("metadata_json")
    private String metadataJson;

    @TableField("ui_schema_json")
    private String uiSchemaJson;

    @TableField("infer_backend")
    private String inferBackend;

    @TableField("status")
    private String status;

    @TableField("current_model_version_id")
    private String currentModelVersionId;

    /** 算法简要描述，DB列名 description */
    @TableField("description")
    private String description;

    /** 适合的业务维度：设备状态监测 / 工艺参数监控 / 质量检测 / 通用，DB列名 business_dimension */
    @TableField("business_dimension")
    private String businessDimension;

    @TableField(value = "version_lock") @Version private Integer versionLock;

    @TableLogic private Boolean isDeleted;

    @TableField(value = "created_at", fill = FieldFill.INSERT) private OffsetDateTime createdAt;
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE) private OffsetDateTime updatedAt;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;
}
