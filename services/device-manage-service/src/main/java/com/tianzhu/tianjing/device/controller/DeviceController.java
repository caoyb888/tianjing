package com.tianzhu.tianjing.device.controller;

import com.tianzhu.tianjing.common.response.ApiResponse;
import com.tianzhu.tianjing.common.response.PageResult;
import com.tianzhu.tianjing.common.security.TianjingUserDetails;
import com.tianzhu.tianjing.device.domain.CameraHealthRecord;
import com.tianzhu.tianjing.device.dto.DeviceDetail;
import com.tianzhu.tianjing.device.dto.DeviceRegisterRequest;
import com.tianzhu.tianjing.device.service.DeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 设备管理接口
 * 规范：API 接口规范 V3.1 §6.3（5 个端点，含 Sprint 5 延期的 live-stream）
 */
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    /**
     * GET /devices — 查询摄像头列表
     */
    @GetMapping
    public ApiResponse<PageResult<DeviceDetail>> listDevices(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String scene_id,
            @RequestParam(required = false) String health_status) {
        return ApiResponse.page(deviceService.listDevices(page, size, scene_id, health_status));
    }

    /**
     * POST /devices — 注册摄像头设备（RTSP URL AES-256 加密存储）
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SCENE_EDITOR', 'ADMIN')")
    public ApiResponse<DeviceDetail> registerDevice(
            @Valid @RequestBody DeviceRegisterRequest request,
            @AuthenticationPrincipal TianjingUserDetails user) {
        return ApiResponse.ok(deviceService.registerDevice(request, user.getUsername()));
    }

    /**
     * GET /devices/{device_code} — 查询单台摄像头
     */
    @GetMapping("/{device_code}")
    public ApiResponse<DeviceDetail> getDevice(@PathVariable("device_code") String deviceCode) {
        return ApiResponse.ok(deviceService.getDevice(deviceCode));
    }

    /**
     * PUT /devices/{device_code} — 更新设备信息
     */
    @PutMapping("/{device_code}")
    @PreAuthorize("hasAnyRole('SCENE_EDITOR', 'ADMIN')")
    public ApiResponse<DeviceDetail> updateDevice(
            @PathVariable("device_code") String deviceCode,
            @Valid @RequestBody DeviceRegisterRequest request,
            @AuthenticationPrincipal TianjingUserDetails user) {
        return ApiResponse.ok(deviceService.updateDevice(deviceCode, request, user.getUsername()));
    }

    /**
     * DELETE /devices/{device_code} — 删除设备
     */
    @DeleteMapping("/{device_code}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteDevice(@PathVariable("device_code") String deviceCode) {
        deviceService.deleteDevice(deviceCode);
        return ApiResponse.ok();
    }

    /**
     * GET /devices/{device_code}/health — 设备健康检测历史（接口 #17）
     */
    @GetMapping("/{device_code}/health")
    public ApiResponse<PageResult<CameraHealthRecord>> getHealthHistory(
            @PathVariable("device_code") String deviceCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.page(deviceService.listHealthHistory(deviceCode, page, size));
    }

    /**
     * GET /devices/{device_code}/live-stream — 获取实时流地址
     * ⚠️ Sprint 5 延期实现（依赖现场摄像头安装与流媒体服务部署）
     * Sprint 1-4 返回 HTTP 501，前端展示占位提示"摄像头接入后可用"
     */
    @GetMapping("/{device_code}/live-stream")
    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
    public ApiResponse<Void> getLiveStream(@PathVariable("device_code") String deviceCode) {
        return ApiResponse.error(2013, "摄像头接入后可用（Sprint 5 实现）",
                "此功能依赖现场摄像头安装与流媒体服务部署，预计 Sprint 5 上线");
    }
}
