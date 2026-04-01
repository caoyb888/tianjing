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

    public PageResult<DeviceDetail> listDevices(int page, int size, String sceneId, String healthStatus) {
        Page<CameraDevice> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<CameraDevice> wrapper = new LambdaQueryWrapper<CameraDevice>()
                .eq(StringUtils.isNotBlank(sceneId), CameraDevice::getSceneId, sceneId)
                .eq(StringUtils.isNotBlank(healthStatus), CameraDevice::getHealthStatus, healthStatus)
                .orderByDesc(CameraDevice::getCreatedAt);

        var result = deviceMapper.selectPage(pageParam, wrapper);
        List<DeviceDetail> items = result.getRecords().stream().map(DeviceDetail::from).toList();
        return PageResult.of(result.getTotal(), page, size, items);
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
        device.setHealthStatus("ONLINE");
        device.setCreatedBy(operator);

        // SECURITY: RTSP URL AES-256 加密存储
        if (StringUtils.isNotBlank(request.rtspUrl()) && StringUtils.isNotBlank(aesKey)) {
            device.setRtspUrlEncrypted(AesEncryptUtil.encrypt(request.rtspUrl(), aesKey));
        } else if (StringUtils.isNotBlank(request.rtspUrl())) {
            log.warn("AES 密钥未配置，RTSP URL 将明文存储（仅限开发环境）");
            device.setRtspUrlEncrypted(request.rtspUrl());
        }

        deviceMapper.insert(device);
        log.info("注册摄像头设备 device_code={} operator={}", device.getDeviceCode(), operator);
        return DeviceDetail.from(device);
    }

    @Transactional
    public DeviceDetail updateDevice(String deviceCode, DeviceRegisterRequest request, String operator) {
        CameraDevice device = findOrThrow(deviceCode);
        if (request.deviceName() != null) device.setDeviceName(request.deviceName());
        if (request.locationDesc() != null) device.setLocationDesc(request.locationDesc());
        if (request.firmwareVersion() != null) device.setFirmwareVersion(request.firmwareVersion());
        if (StringUtils.isNotBlank(request.rtspUrl()) && StringUtils.isNotBlank(aesKey)) {
            device.setRtspUrlEncrypted(AesEncryptUtil.encrypt(request.rtspUrl(), aesKey));
        }
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
