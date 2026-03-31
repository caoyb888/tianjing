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

    private String calibrationId;
    private String sceneId;
    private String deviceCode;

    /** 参考物实际长度（mm）*/
    private BigDecimal refLengthMm;

    /** 测量点1坐标（px）*/
    private Integer pt1X;
    private Integer pt1Y;

    /** 测量点2坐标（px）*/
    private Integer pt2X;
    private Integer pt2Y;

    /** 计算所得像素比例：mm/px = refLengthMm / distance(pt1, pt2) */
    private BigDecimal scaleMmPerPx;

    /** 标定状态：ACTIVE / SUPERSEDED */
    private String status;

    /** 关联的帧图像 MinIO 路径 */
    private String frameImageUrl;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    private String createdBy;
}
