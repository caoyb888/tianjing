package com.tianzhu.tianjing.health.controller;

import com.tianzhu.tianjing.common.response.ApiResponse;
import com.tianzhu.tianjing.health.repository.CameraDeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 感知健康查询接口（前端面向）
 * 规范：API 接口规范 V3.1 §6.15
 * 数据源：camera_device（health_status / health_score）+ camera_health_record（历史）
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/health-monitor")
@RequiredArgsConstructor
public class HealthQueryController {

    private final CameraDeviceMapper cameraDeviceMapper;

    /** GET /api/v1/health-monitor/summary — 感知健康总览 */
    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> getSummary() {
        int total   = cameraDeviceMapper.countTotal();
        int healthy = cameraDeviceMapper.countByStatus("HEALTHY");
        int offline = cameraDeviceMapper.countByStatus("OFFLINE");
        int degraded = cameraDeviceMapper.countByStatus("DEGRADED");
        Double avgScore = cameraDeviceMapper.avgHealthScore();
        return ApiResponse.ok(Map.of(
                "total_cameras", total,
                "healthy",       healthy,
                "warning",       degraded,
                "offline",       offline,
                "avg_health_score", avgScore != null ? Math.round(avgScore * 10.0) / 10.0 : 0.0
        ));
    }

    /** GET /api/v1/health-monitor/cameras — 摄像头健康列表 */
    @GetMapping("/cameras")
    public ApiResponse<Map<String, Object>> getCameras(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sceneId,
            @RequestParam(required = false) String factory,
            @RequestParam(required = false) String status) {
        int total  = cameraDeviceMapper.countTotal();
        int offset = (page - 1) * size;
        List<Map<String, Object>> rows = cameraDeviceMapper.selectCameraList(size, offset);

        List<Map<String, Object>> items = rows.stream()
                .filter(r -> sceneId == null || sceneId.equals(r.get("scene_id")))
                .filter(r -> factory == null || factory.equals(r.get("factory_code")))
                .filter(r -> {
                    if (status == null) return true;
                    String dbStatus = (String) r.get("health_status");
                    String mapped = switch (dbStatus == null ? "" : dbStatus) {
                        case "HEALTHY"  -> "ONLINE";
                        case "DEGRADED" -> "DEGRADED";
                        case "OFFLINE"  -> "OFFLINE";
                        default         -> "UNKNOWN";
                    };
                    return status.equals(mapped);
                })
                .map(r -> {
                    String dbStatus = (String) r.get("health_status");
                    String uiStatus = switch (dbStatus == null ? "" : dbStatus) {
                        case "HEALTHY"  -> "ONLINE";
                        case "DEGRADED" -> "DEGRADED";
                        case "OFFLINE"  -> "OFFLINE";
                        default         -> "UNKNOWN";
                    };
                    Object scoreRaw = r.get("health_score");
                    int score = scoreRaw instanceof Number n ? n.intValue() : 0;
                    return Map.<String, Object>of(
                            "cameraId",    r.getOrDefault("device_code", ""),
                            "sceneId",     r.getOrDefault("scene_id", ""),
                            "factory",     r.getOrDefault("factory_code", ""),
                            "status",      uiStatus,
                            "healthScore", score
                    );
                })
                .collect(Collectors.toList());

        // 汇总（支持筛选后重新计算）
        long online   = items.stream().filter(i -> "ONLINE".equals(i.get("status"))).count();
        long degraded = items.stream().filter(i -> "DEGRADED".equals(i.get("status"))).count();
        long offline  = items.stream().filter(i -> "OFFLINE".equals(i.get("status"))).count();

        return ApiResponse.ok(Map.of(
                "total", total,
                "page",  page,
                "size",  size,
                "items", items,
                "summary", Map.of(
                        "total",    items.size(),
                        "online",   online,
                        "degraded", degraded,
                        "offline",  offline
                )
        ));
    }

    /** GET /api/v1/health-monitor/cameras/{cameraId}/history — 摄像头健康历史 */
    @GetMapping("/cameras/{cameraId}/history")
    public ApiResponse<List<Map<String, Object>>> getCameraHistory(
            @PathVariable String cameraId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        List<Map<String, Object>> history = cameraDeviceMapper.selectHealthHistory(cameraId);
        return ApiResponse.ok(history);
    }

    /** POST /api/v1/health-monitor/cameras/{cameraId}/repair-ticket — 创建维修工单 */
    @PostMapping("/cameras/{cameraId}/repair-ticket")
    public ApiResponse<Map<String, Object>> createRepairTicket(
            @PathVariable String cameraId,
            @RequestBody Map<String, Object> body) {
        log.info("创建维修工单 cameraId={} reason={}", cameraId, body.get("reason"));
        return ApiResponse.ok(Map.of(
                "ticket_id", "TICKET-" + System.currentTimeMillis(),
                "status",    "CREATED"
        ));
    }
}
