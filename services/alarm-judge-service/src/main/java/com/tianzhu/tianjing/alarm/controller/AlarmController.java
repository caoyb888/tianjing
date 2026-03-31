package com.tianzhu.tianjing.alarm.controller;

import com.tianzhu.tianjing.alarm.domain.AlarmRecord;
import com.tianzhu.tianjing.alarm.dto.AlarmFeedbackRequest;
import com.tianzhu.tianjing.alarm.dto.AlarmQueryParams;
import com.tianzhu.tianjing.alarm.service.AlarmService;
import com.tianzhu.tianjing.common.response.ApiResponse;
import com.tianzhu.tianjing.common.response.PageResult;
import com.tianzhu.tianjing.common.security.TianjingUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

/**
 * 告警管理接口
 * 规范：API 接口规范 V3.1 §6.7（4 个端点）
 */
@RestController
@RequestMapping("/api/v1/alarms")
@RequiredArgsConstructor
public class AlarmController {

    private final AlarmService alarmService;

    /**
     * GET /alarms — 查询告警列表
     */
    @GetMapping
    public ApiResponse<PageResult<AlarmRecord>> listAlarms(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String scene_id,
            @RequestParam(required = false) String alarm_level,
            @RequestParam(required = false) String factory_code,
            @RequestParam(required = false) Boolean is_sandbox,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime start_time,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime end_time) {

        AlarmQueryParams params = new AlarmQueryParams(page, size, scene_id, alarm_level,
                factory_code, is_sandbox, start_time, end_time);
        return ApiResponse.page(alarmService.listAlarms(params));
    }

    /**
     * GET /alarms/{alarm_id} — 查询单条告警详情
     */
    @GetMapping("/{alarm_id}")
    public ApiResponse<AlarmRecord> getAlarm(@PathVariable("alarm_id") String alarmId) {
        return ApiResponse.ok(alarmService.getAlarm(alarmId));
    }

    /**
     * POST /alarms/{alarm_id}/feedback — 提交告警人工反馈
     */
    @PostMapping("/{alarm_id}/feedback")
    public ApiResponse<Void> submitFeedback(
            @PathVariable("alarm_id") String alarmId,
            @Valid @RequestBody AlarmFeedbackRequest request,
            @AuthenticationPrincipal TianjingUserDetails user) {
        alarmService.submitFeedback(alarmId, request, user.getUsername());
        return ApiResponse.ok();
    }

    /**
     * POST /alarms/{alarm_id}/retry — 重推失败告警（仅 FAILED 状态）
     */
    @PostMapping("/{alarm_id}/retry")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> retryPush(@PathVariable("alarm_id") String alarmId) {
        alarmService.retryPush(alarmId);
        return ApiResponse.ok();
    }
}
