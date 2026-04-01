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
    private String pluginId;
    private String pluginName;
    private String version;
    private String pluginType;
    private String backbone;
    private String supportedScenes;
    private String hardwareRequirementsJson;
    private String accuracyMetricsJson;
    private String uiSchemaJson;
    private String inferBackend;
    private String status;
    @TableField(value = "version_lock") @Version private Integer versionLock;
    @TableLogic private Boolean isDeleted;
    @TableField(fill = FieldFill.INSERT) private OffsetDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE) private OffsetDateTime updatedAt;
    private String createdBy;
}
