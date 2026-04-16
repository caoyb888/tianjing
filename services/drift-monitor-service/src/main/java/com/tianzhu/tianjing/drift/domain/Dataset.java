package com.tianzhu.tianjing.drift.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

/**
 * 训练数据集（对应 tianjing_train 库 dataset 表）
 */
@Data
@TableName("dataset")
public class Dataset {
    @TableId(type = IdType.AUTO) private Long id;

    @TableField("dataset_code")
    private String datasetCode;

    @TableField("dataset_name")
    private String datasetName;

    @TableField("scene_id")
    private String sceneId;

    @TableField("factory_code")
    private String factoryCode;

    @TableField("source_type")
    private String sourceType;

    @TableField("total_samples")
    private Integer totalSamples;

    @TableField("positive_samples")
    private Integer positiveSamples;

    @TableField("negative_samples")
    private Integer negativeSamples;

    @TableField("minio_prefix")
    private String minioPrefix;

    @TableField("is_deleted")
    private Boolean isDeleted;

    @TableField("created_at")
    private OffsetDateTime createdAt;

    @TableField("created_by")
    private String createdBy;

    @Version
    @TableField("version")
    private Integer version;
}
