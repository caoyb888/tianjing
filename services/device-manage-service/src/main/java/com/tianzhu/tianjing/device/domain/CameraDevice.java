package com.tianzhu.tianjing.device.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 摄像头设备实体（对应 camera_device 表）
 * RTSP URL AES-256 加密存储（规范：CLAUDE.md §10.2）
 */
@Data
@TableName("camera_device")
public class CameraDevice {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("device_code")
    private String deviceCode;

    @TableField("device_name")
    private String deviceName;

    @TableField("scene_id")
    private String sceneId;

    @TableField("ip_address")
    private String ipAddress;

    @TableField("mac_address")
    private String macAddress;

    @TableField("vendor")
    private String vendor;

    @TableField("firmware_version")
    private String firmwareVersion;

    /** RTSP URL — AES-256-GCM 加密存储，响应时脱敏（DB 列名：rtsp_url） */
    @TableField("rtsp_url")
    private String rtspUrl;

    @TableField("protocol")
    private String protocol;

    @TableField("resolution_width")
    private Integer resolutionWidth;

    @TableField("resolution_height")
    private Integer resolutionHeight;

    @TableField("fps")
    private Integer fps;

    @TableField("location_desc")
    private String locationDesc;

    @TableField("edge_node_id")
    private String edgeNodeId;

    @TableField("is_supplement_light")
    private Boolean isSupplementLight;

    /** 设备健康状态：HEALTHY / BLURRY / OFFLINE / SHIFTED / UNKNOWN */
    @TableField("health_status")
    private String healthStatus;

    @TableField("last_health_check")
    private OffsetDateTime lastHealthCheck;

    @TableField("health_score")
    private Integer healthScore;

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
