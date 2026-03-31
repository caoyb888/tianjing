package com.tianzhu.tianjing.calibration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianzhu.tianjing.calibration.domain.CalibrationRecord;
import com.tianzhu.tianjing.calibration.dto.CalibrationRequest;
import com.tianzhu.tianjing.calibration.repository.CalibrationMapper;
import com.tianzhu.tianjing.common.exception.BusinessException;
import com.tianzhu.tianjing.common.exception.ErrorCode;
import com.tianzhu.tianjing.common.response.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 在线标定业务服务
 * 规范：API 接口规范 V3.1 §6.4
 *
 * scale_mm_per_px 计算公式：
 *   distance = sqrt((pt2x-pt1x)^2 + (pt2y-pt1y)^2)
 *   scale = refLengthMm / distance
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalibrationService {

    private final CalibrationMapper calibrationMapper;

    @Transactional
    public CalibrationRecord submitCalibration(String sceneId, CalibrationRequest request, String operator) {
        // 将同场景旧标定记录状态置为 SUPERSEDED
        calibrationMapper.update(new LambdaUpdateWrapper<CalibrationRecord>()
                .eq(CalibrationRecord::getSceneId, sceneId)
                .eq(CalibrationRecord::getStatus, "ACTIVE")
                .set(CalibrationRecord::getStatus, "SUPERSEDED"));

        double dx = request.pt2X() - request.pt1X();
        double dy = request.pt2Y() - request.pt1Y();
        double distancePx = Math.sqrt(dx * dx + dy * dy);

        if (distancePx < 1.0) {
            throw BusinessException.of(ErrorCode.PARAM_MISSING, "标定两点距离过近，无法计算比例");
        }

        BigDecimal scaleMmPerPx = request.refLengthMm()
                .divide(BigDecimal.valueOf(distancePx), 6, RoundingMode.HALF_UP);

        CalibrationRecord record = new CalibrationRecord();
        record.setCalibrationId("CAL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        record.setSceneId(sceneId);
        record.setDeviceCode(request.deviceCode());
        record.setRefLengthMm(request.refLengthMm());
        record.setPt1X(request.pt1X());
        record.setPt1Y(request.pt1Y());
        record.setPt2X(request.pt2X());
        record.setPt2Y(request.pt2Y());
        record.setScaleMmPerPx(scaleMmPerPx);
        record.setStatus("ACTIVE");
        record.setFrameImageUrl(request.frameImageUrl());
        record.setCreatedBy(operator);

        calibrationMapper.insert(record);
        log.info("提交标定记录 scene_id={} scale_mm_per_px={} operator={}", sceneId, scaleMmPerPx, operator);
        return record;
    }

    public PageResult<CalibrationRecord> getCalibrationHistory(String sceneId, int page, int size) {
        Page<CalibrationRecord> pageParam = new Page<>(page, size);
        var result = calibrationMapper.selectPage(pageParam, new LambdaQueryWrapper<CalibrationRecord>()
                .eq(CalibrationRecord::getSceneId, sceneId)
                .orderByDesc(CalibrationRecord::getCreatedAt));
        return PageResult.of(result.getTotal(), page, size, result.getRecords());
    }
}
