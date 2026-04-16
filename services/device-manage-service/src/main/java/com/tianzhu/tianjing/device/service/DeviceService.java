package com.tianzhu.tianjing.device.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianzhu.tianjing.common.exception.BusinessException;
import com.tianzhu.tianjing.common.exception.ErrorCode;
import com.tianzhu.tianjing.common.response.PageResult;
import com.tianzhu.tianjing.common.util.AesEncryptUtil;
import com.tianzhu.tianjing.device.domain.CameraDevice;
import com.tianzhu.tianjing.device.dto.DeviceDetail;
import com.tianzhu.tianjing.device.dto.DeviceRegisterRequest;
import com.tianzhu.tianjing.device.domain.CameraHealthRecord;
import com.tianzhu.tianjing.device.repository.CameraDeviceMapper;
import com.tianzhu.tianjing.device.repository.CameraHealthRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 设备管理业务服务
 * 规范：API 接口规范 V3.1 §6.3
 *
 * 安全注意：RTSP URL 含密码，AES-256-GCM 加密后存储，响应时脱敏（仅返回 IP:port）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final CameraDeviceMapper deviceMapper;
    private final CameraHealthRecordMapper healthMapper;

    // SECURITY: AES 密钥从 K8s Secret 注入，禁止硬编码
    @Value("${tianjing.security.aes-key:}")
    private String aesKey;

    public PageResult<DeviceDetail> listDevices(int page, int size,
                                                String sceneId, String healthStatus,
                                                String factory, String status, String keyword) {
        Page<CameraDevice> pageParam = new Page<>(page, size);
        // factory → scene_id 前缀（pellet→SCENE-PELLET, sintering→SCENE-SINTER, ...）
        String scenePrefix = factoryToScenePrefix(factory);
        // status (online/offline/warning) → DB health_status
        String dbStatus = uiStatusToDb(status);

        LambdaQueryWrapper<CameraDevice> wrapper = new LambdaQueryWrapper<CameraDevice>()
                .eq(StringUtils.isNotBlank(sceneId), CameraDevice::getSceneId, sceneId)
                .eq(StringUtils.isNotBlank(dbStatus), CameraDevice::getHealthStatus, dbStatus)
                .likeRight(StringUtils.isNotBlank(scenePrefix), CameraDevice::getSceneId, scenePrefix)
                .and(StringUtils.isNotBlank(keyword), w -> w
                        .like(CameraDevice::getDeviceName, keyword)
                        .or().like(CameraDevice::getDeviceCode, keyword)
                        .or().like(CameraDevice::getIpAddress, keyword))
                .orderByDesc(CameraDevice::getCreatedAt);

        var result = deviceMapper.selectPage(pageParam, wrapper);
        List<DeviceDetail> items = result.getRecords().stream().map(DeviceDetail::from).toList();
        return PageResult.of(result.getTotal(), page, size, items);
    }

    private static String factoryToScenePrefix(String factory) {
        if (StringUtils.isBlank(factory)) return null;
        return switch (factory.toLowerCase()) {
            case "pellet"    -> "SCENE-PELLET";
            case "sintering" -> "SCENE-SINTER";
            case "steel"     -> "SCENE-STEEL";
            case "section"   -> "SCENE-SECTION";
            case "strip"     -> "SCENE-STRIP";
            default          -> null;
        };
    }

    private static String uiStatusToDb(String uiStatus) {
        if (StringUtils.isBlank(uiStatus)) return null;
        return switch (uiStatus.toLowerCase()) {
            case "online"  -> "HEALTHY";
            case "offline" -> "OFFLINE";
            case "warning" -> "DEGRADED";
            default        -> null;
        };
    }

    public DeviceDetail getDevice(String deviceCode) {
        CameraDevice device = findOrThrow(deviceCode);
        return DeviceDetail.from(device);
    }

    @Transactional
    public DeviceDetail registerDevice(DeviceRegisterRequest request, String operator) {
        // 校验设备编码唯一性
        Long existing = deviceMapper.selectCount(new LambdaQueryWrapper<CameraDevice>()
                .eq(CameraDevice::getDeviceCode, request.deviceCode())
                );
        if (existing > 0) {
            throw BusinessException.of(ErrorCode.CAMERA_BIND_CONFLICT,
                    "设备编码已存在: " + request.deviceCode());
        }

        CameraDevice device = new CameraDevice();
        device.setDeviceCode(request.deviceCode());
        device.setDeviceName(request.deviceName());
        device.setSceneId(request.sceneId());
        device.setIpAddress(request.ipAddress());
        device.setMacAddress(request.macAddress());
        device.setVendor(request.vendor());
        device.setFirmwareVersion(request.firmwareVersion());
        device.setProtocol(request.protocol() != null ? request.protocol() : "RTSP");
        device.setResolutionWidth(request.resolutionWidth());
        device.setResolutionHeight(request.resolutionHeight());
        device.setFps(request.fps());
        device.setLocationDesc(request.locationDesc());
        device.setIsSupplementLight(request.isSupplementLight());
        device.setHealthStatus("UNKNOWN");
        device.setCreatedBy(operator);

        // SECURITY: RTSP URL AES-256 加密存储
        if (StringUtils.isNotBlank(request.rtspUrl()) && StringUtils.isNotBlank(aesKey)) {
            device.setRtspUrl(AesEncryptUtil.encrypt(request.rtspUrl(), aesKey));
        } else if (StringUtils.isNotBlank(request.rtspUrl())) {
            log.warn("AES 密钥未配置，RTSP URL 将明文存储（仅限开发环境）");
            device.setRtspUrl(request.rtspUrl());
        }

        deviceMapper.insert(device);
        log.info("注册摄像头设备 device_code={} operator={}", device.getDeviceCode(), operator);
        return DeviceDetail.from(device);
    }

    @Transactional
    public DeviceDetail updateDevice(String deviceCode, DeviceRegisterRequest request, String operator) {
        CameraDevice device = findOrThrow(deviceCode);
        if (StringUtils.isNotBlank(request.deviceName()))     device.setDeviceName(request.deviceName());
        if (StringUtils.isNotBlank(request.sceneId()))        device.setSceneId(request.sceneId());
        if (StringUtils.isNotBlank(request.ipAddress()))      device.setIpAddress(request.ipAddress());
        if (StringUtils.isNotBlank(request.macAddress()))     device.setMacAddress(request.macAddress());
        if (StringUtils.isNotBlank(request.vendor()))         device.setVendor(request.vendor());
        if (StringUtils.isNotBlank(request.firmwareVersion())) device.setFirmwareVersion(request.firmwareVersion());
        if (StringUtils.isNotBlank(request.protocol()))       device.setProtocol(request.protocol());
        if (request.resolutionWidth() != null)                device.setResolutionWidth(request.resolutionWidth());
        if (request.resolutionHeight() != null)               device.setResolutionHeight(request.resolutionHeight());
        if (request.fps() != null)                            device.setFps(request.fps());
        if (request.locationDesc() != null)                   device.setLocationDesc(request.locationDesc());
        if (request.isSupplementLight() != null)              device.setIsSupplementLight(request.isSupplementLight());
        if (StringUtils.isNotBlank(request.rtspUrl()) && StringUtils.isNotBlank(aesKey)) {
            device.setRtspUrl(AesEncryptUtil.encrypt(request.rtspUrl(), aesKey));
        }
        device.setUpdatedBy(operator);
        deviceMapper.updateById(device);
        log.info("更新设备信息 device_code={} operator={}", deviceCode, operator);
        return DeviceDetail.from(device);
    }

    @Transactional
    public void deleteDevice(String deviceCode) {
        CameraDevice device = findOrThrow(deviceCode);
        deviceMapper.deleteById(device.getId());
        log.info("软删除设备 device_code={}", deviceCode);
    }

    /**
     * 查询设备健康检测历史（接口 #17）
     * 规范：CLAUDE.md §4.2（health-monitor-service 写入 camera_health_record，此处只读）
     */
    public PageResult<CameraHealthRecord> listHealthHistory(String deviceCode, int page, int size) {
        findOrThrow(deviceCode); // 校验设备存在
        Page<CameraHealthRecord> pageParam = new Page<>(page, size);
        var result = healthMapper.selectPage(pageParam,
                new LambdaQueryWrapper<CameraHealthRecord>()
                        .eq(CameraHealthRecord::getDeviceCode, deviceCode)
                        .orderByDesc(CameraHealthRecord::getCheckAt));
        return PageResult.of(result.getTotal(), page, size, result.getRecords());
    }

    private CameraDevice findOrThrow(String deviceCode) {
        CameraDevice device = deviceMapper.selectOne(new LambdaQueryWrapper<CameraDevice>()
                .eq(CameraDevice::getDeviceCode, deviceCode)
                );
        if (device == null) {
            throw BusinessException.notFound(ErrorCode.DEVICE_NOT_FOUND);
        }
        return device;
    }
}
