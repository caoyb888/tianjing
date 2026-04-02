package com.tianzhu.tianjing.device.dto;

import com.tianzhu.tianjing.device.domain.CameraDevice;

import java.time.OffsetDateTime;

/**
 * 设备详情响应体
 * 字段名与前端 DeviceInfo 类型严格对齐（camelCase）
 * RTSP URL 脱敏：仅返回 IP 地址
 */
public record DeviceDetail(
        String deviceCode,
        String deviceName,
        String sceneId,
        String factory,          // 从 scene_id 前缀推断，如 SCENE-PELLET-001 → PELLET
        String ipAddress,
        String macAddress,
        String vendor,
        String firmwareVersion,
        String protocol,
        Integer resolutionWidth,
        Integer resolutionHeight,
        Integer fps,
        String locationDesc,
        Boolean isSupplementLight,
        String healthStatus,     // 原始值：HEALTHY / BLURRY / OFFLINE / SHIFTED / UNKNOWN
        String status,           // 前端语义值：online / offline / warning
        String lastHeartbeat,    // ISO 8601 字符串，来自 lastHealthCheck
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static DeviceDetail from(CameraDevice device) {
        String status = mapStatus(device.getHealthStatus());
        String factory = extractFactory(device.getSceneId());
        String lastHeartbeat = device.getLastHealthCheck() != null
                ? device.getLastHealthCheck().toString() : null;

        return new DeviceDetail(
                device.getDeviceCode(),
                device.getDeviceName(),
                device.getSceneId(),
                factory,
                device.getIpAddress(),
                device.getMacAddress(),
                device.getVendor(),
                device.getFirmwareVersion(),
                device.getProtocol(),
                device.getResolutionWidth(),
                device.getResolutionHeight(),
                device.getFps(),
                device.getLocationDesc(),
                device.getIsSupplementLight(),
                device.getHealthStatus(),
                status,
                lastHeartbeat,
                device.getCreatedAt(),
                device.getUpdatedAt()
        );
    }

    /** 将数据库健康状态映射为前端语义状态 */
    private static String mapStatus(String healthStatus) {
        if (healthStatus == null) return "offline";
        return switch (healthStatus) {
            case "HEALTHY"  -> "online";
            case "OFFLINE"  -> "offline";
            case "BLURRY", "SHIFTED" -> "warning";
            default         -> "offline";   // UNKNOWN 等
        };
    }

    /** 从场景 ID 推断厂部代码，如 SCENE-PELLET-001 → PELLET */
    private static String extractFactory(String sceneId) {
        if (sceneId == null) return null;
        // 格式：SCENE-{FACTORY}-{序号}
        String[] parts = sceneId.split("-");
        return parts.length >= 2 ? parts[1] : null;
    }
}
