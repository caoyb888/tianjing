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

    private String deviceCode;
    private String deviceName;
    private String sceneId;
    private String ipAddress;
    private String macAddress;
    private String vendor;
    private String firmwareVersion;

    /** RTSP URL — AES-256-GCM 加密存储，响应时脱敏 */
    private String rtspUrlEncrypted;

    private String protocol;
    private Integer resolutionWidth;
    private Integer resolutionHeight;
    private Integer fps;
    private String locationDesc;
    private Boolean isSupplementLight;

    /** 设备状态：ONLINE / OFFLINE / MAINTENANCE */
    private String healthStatus;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    private String createdBy;
}
