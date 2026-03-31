package com.tianzhu.tianjing.device.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tianzhu.tianjing.device.domain.CameraDevice;

import java.time.OffsetDateTime;

/**
 * 设备详情响应体
 * RTSP URL 脱敏：仅返回 host 部分（去掉用户名密码）
 */
public record DeviceDetail(
        @JsonProperty("device_code") String deviceCode,
        @JsonProperty("device_name") String deviceName,
        @JsonProperty("scene_id") String sceneId,
        @JsonProperty("ip_address") String ipAddress,
        @JsonProperty("mac_address") String macAddress,
        String vendor,
        @JsonProperty("firmware_version") String firmwareVersion,
        @JsonProperty("rtsp_host") String rtspHost,   // 脱敏：仅 IP:port
        String protocol,
        @JsonProperty("resolution_width") Integer resolutionWidth,
        @JsonProperty("resolution_height") Integer resolutionHeight,
        Integer fps,
        @JsonProperty("location_desc") String locationDesc,
        @JsonProperty("is_supplement_light") Boolean isSupplementLight,
        @JsonProperty("health_status") String healthStatus,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("updated_at") OffsetDateTime updatedAt
) {
    public static DeviceDetail from(CameraDevice device) {
        return new DeviceDetail(
                device.getDeviceCode(), device.getDeviceName(), device.getSceneId(),
                device.getIpAddress(), device.getMacAddress(), device.getVendor(),
                device.getFirmwareVersion(),
                device.getIpAddress(),  // rtsp_host：仅返回 IP（原始加密，无需解密）
                device.getProtocol(),
                device.getResolutionWidth(), device.getResolutionHeight(), device.getFps(),
                device.getLocationDesc(), device.getIsSupplementLight(),
                device.getHealthStatus(),
                device.getCreatedAt(), device.getUpdatedAt()
        );
    }
}
