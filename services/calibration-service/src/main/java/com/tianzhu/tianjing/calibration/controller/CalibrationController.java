package com.tianzhu.tianjing.calibration.controller;

import com.tianzhu.tianjing.calibration.domain.CalibrationRecord;
import com.tianzhu.tianjing.calibration.dto.CalibrationRequest;
import com.tianzhu.tianjing.calibration.service.CalibrationService;
import com.tianzhu.tianjing.common.response.ApiResponse;
import com.tianzhu.tianjing.common.response.PageResult;
import com.tianzhu.tianjing.common.security.TianjingUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 在线标定接口
 * 规范：API 接口规范 V3.1 §6.4（2 个端点）
 */
@RestController
@RequestMapping("/api/v1/scenes/{scene_id}/calibrations")
@RequiredArgsConstructor
public class CalibrationController {

    private final CalibrationService calibrationService;

    /**
     * POST /scenes/{scene_id}/calibrations — 提交在线标定结果
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SCENE_EDITOR', 'ADMIN')")
    public ApiResponse<CalibrationRecord> submitCalibration(
            @PathVariable("scene_id") String sceneId,
            @Valid @RequestBody CalibrationRequest request,
            @AuthenticationPrincipal TianjingUserDetails user) {
        return ApiResponse.ok(calibrationService.submitCalibration(sceneId, request, user.getUsername()));
    }

    /**
     * GET /scenes/{scene_id}/calibrations — 查询标定历史
     */
    @GetMapping
    public ApiResponse<PageResult<CalibrationRecord>> getHistory(
            @PathVariable("scene_id") String sceneId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.page(calibrationService.getCalibrationHistory(sceneId, page, size));
    }
}
