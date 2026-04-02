package com.tianzhu.tianjing.calibration.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 在线标定记录（对应 calibration_record 表）
 * scale_mm_per_px：像素到毫米比例，由测量点对计算
 */
@Data
@TableName("calibration_record")
public class CalibrationRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("calibration_id")
    private String calibrationId;

    @TableField("scene_id")
    private String sceneId;

    @TableField("device_code")
    private String deviceCode;

    /** 参考物实际长度（mm），DB列名 ref_distance_mm */
    @TableField("ref_distance_mm")
    private BigDecimal refDistanceMm;

    /** 测量点1坐标（px），DB列名 pixel_p1_x / pixel_p1_y */
    @TableField("pixel_p1_x")
    private Integer pixelP1X;

    @TableField("pixel_p1_y")
    private Integer pixelP1Y;

    /** 测量点2坐标（px），DB列名 pixel_p2_x / pixel_p2_y */
    @TableField("pixel_p2_x")
    private Integer pixelP2X;

    @TableField("pixel_p2_y")
    private Integer pixelP2Y;

    /** 两点像素距离（由 sqrt((p2x-p1x)²+(p2y-p1y)²) 计算） */
    @TableField("pixel_distance")
    private Integer pixelDistance;

    /** 计算所得像素比例：mm/px = refDistanceMm / pixelDistance */
    @TableField("scale_mm_per_px")
    private BigDecimal scaleMmPerPx;

    @TableField("is_active")
    private Boolean isActive;

    @TableField("calibrated_by")
    private String calibratedBy;

    @TableField("remark")
    private String remark;

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

    @Version
    private Integer version;
}
