package com.tianzhu.tianjing.health.controller;

import com.tianzhu.tianjing.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 感知健康查询接口（前端面向）
 * 规范：API 接口规范 V3.1 §6.15
 *
 * 当前为基础实现，历史健康数据写入由 frame-extract/preprocess 通过内部接口上报。
 * 前端查询接口返回聚合摘要与摄像头状态列表。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/health-monitor")
public class HealthQueryController {

    /** GET /api/v1/health-monitor/summary — 感知健康总览 */
    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> getSummary() {
        return ApiResponse.ok(Map.of(
                "total_cameras", 0,
                "healthy", 0,
                "warning", 0,
                "offline", 0,
                "avg_health_score", 0.0
        ));
    }

    /** GET /api/v1/health-monitor/cameras — 摄像头健康列表 */
    @GetMapping("/cameras")
    public ApiResponse<Map<String, Object>> getCameras(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sceneId,
            @RequestParam(required = false) String factory) {
        return ApiResponse.ok(Map.of(
                "total", 0,
                "page", page,
                "size", size,
                "items", List.of()
        ));
    }

    /** GET /api/v1/health-monitor/cameras/{cameraId}/history — 摄像头健康历史 */
    @GetMapping("/cameras/{cameraId}/history")
    public ApiResponse<List<Object>> getCameraHistory(
            @PathVariable String cameraId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return ApiResponse.ok(List.of());
    }

    /** POST /api/v1/health-monitor/cameras/{cameraId}/repair-ticket — 创建维修工单 */
    @PostMapping("/cameras/{cameraId}/repair-ticket")
    public ApiResponse<Map<String, Object>> createRepairTicket(
            @PathVariable String cameraId,
            @RequestBody Map<String, Object> body) {
        log.info("创建维修工单 cameraId={} reason={}", cameraId, body.get("reason"));
        return ApiResponse.ok(Map.of(
                "ticket_id", "TICKET-" + System.currentTimeMillis(),
                "status", "CREATED"
        ));
    }
}
