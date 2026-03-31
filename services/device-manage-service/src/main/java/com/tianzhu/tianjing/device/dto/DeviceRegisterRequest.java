package com.tianzhu.tianjing.device.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record DeviceRegisterRequest(
        @NotBlank @JsonProperty("device_code") String deviceCode,
        @NotBlank @JsonProperty("device_name") String deviceName,
        @JsonProperty("scene_id") String sceneId,
        @NotBlank @JsonProperty("ip_address") String ipAddress,
        @JsonProperty("mac_address") String macAddress,
        String vendor,
        @JsonProperty("firmware_version") String firmwareVersion,
        @JsonProperty("rtsp_url") String rtspUrl,
        String protocol,
        @JsonProperty("resolution_width") Integer resolutionWidth,
        @JsonProperty("resolution_height") Integer resolutionHeight,
        Integer fps,
        @JsonProperty("location_desc") String locationDesc,
        @JsonProperty("is_supplement_light") Boolean isSupplementLight
) {}
