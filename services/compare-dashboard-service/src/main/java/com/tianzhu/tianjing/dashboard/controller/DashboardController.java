package com.tianzhu.tianjing.dashboard.controller;

import com.tianzhu.tianjing.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 数据看板接口
 * 规范：API 接口规范 V3.1 §6.13（3 个端点）
 * 包含：概览统计 / SSE 实时告警 / 推理趋势
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final JdbcTemplate prodJdbcTemplate;

    /** SSE 客户端列表（线程安全） */
    private final List<SseEmitter> sseClients = new CopyOnWriteArrayList<>();

    public DashboardController(@Qualifier("prodJdbcTemplate") JdbcTemplate prodJdbcTemplate) {
        this.prodJdbcTemplate = prodJdbcTemplate;
    }

    /**
     * GET /dashboard/overview — 平台概览统计
     * 字段名与前端 DashboardView stats 计算属性对齐
     */
    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        // 从生产库实时查询
        int activeScenes = queryCount(
                "SELECT COUNT(*) FROM scene_config WHERE status = 'ACTIVE' AND is_deleted = false");
        int onlineDevices = queryCount(
                "SELECT COUNT(*) FROM camera_device WHERE health_status = 'HEALTHY'");
        int todayAlarms = queryCount(
                "SELECT COUNT(*) FROM alarm_record WHERE created_at >= CURRENT_DATE AND is_sandbox = false");

        Map<String, Object> data = new LinkedHashMap<>();
        // 统计卡片字段（前端 stats computed 使用）
        data.put("active_scenes", activeScenes);
        data.put("online_devices", onlineDevices);
        data.put("today_alarms", todayAlarms);
        data.put("today_inferences", 0);
        // 告警饼图字段（AlarmHeatmapChart 使用）
        data.put("critical_alarms", queryCount(
                "SELECT COUNT(*) FROM alarm_record WHERE created_at >= CURRENT_DATE AND alarm_level = 'CRITICAL' AND is_sandbox = false"));
        data.put("warning_alarms", queryCount(
                "SELECT COUNT(*) FROM alarm_record WHERE created_at >= CURRENT_DATE AND alarm_level = 'WARNING' AND is_sandbox = false"));
        data.put("info_alarms", queryCount(
                "SELECT COUNT(*) FROM alarm_record WHERE created_at >= CURRENT_DATE AND alarm_level = 'INFO' AND is_sandbox = false"));
        // 附加字段
        data.put("total_scenes", queryCount("SELECT COUNT(*) FROM scene_config WHERE is_deleted = false"));
        data.put("avg_infer_latency_ms", 0.0);
        data.put("sandbox_sessions_running", 0);
        data.put("generated_at", OffsetDateTime.now());
        return ApiResponse.ok(data);
    }

    /** 安全查询 COUNT，异常时返回 0 */
    private int queryCount(String sql) {
        try {
            Integer result = prodJdbcTemplate.queryForObject(sql, Integer.class);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.warn("Dashboard 统计查询失败 sql={} error={}", sql, e.getMessage());
            return 0;
        }
    }

    /**
     * GET /dashboard/alarms/realtime — 实时告警 SSE 流
     * 客户端使用 EventSource 订阅；告警判定服务推送新告警后广播
     */
    @GetMapping(value = "/alarms/realtime", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter realtimeAlarms() {
        SseEmitter emitter = new SseEmitter(0L); // 永不超时
        sseClients.add(emitter);
        emitter.onCompletion(() -> sseClients.remove(emitter));
        emitter.onTimeout(() -> sseClients.remove(emitter));
        emitter.onError(e -> sseClients.remove(emitter));

        // 发送连接确认事件
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{\"message\":\"实时告警推流已建立\",\"timestamp\":\"" + OffsetDateTime.now() + "\"}"));
        } catch (Exception e) {
            log.debug("SSE 连接确认发送失败: {}", e.getMessage());
        }
        return emitter;
    }

    /**
     * GET /dashboard/inference-trend — 推理统计趋势（近 N 天）
     * 返回字段与 InferenceTrendChart 对齐：time / count / alarms
     */
    @GetMapping("/inference-trend")
    public ApiResponse<List<Map<String, Object>>> inferenceTrend(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) String scene_id) {
        // 暂返回空趋势数据，Sprint 3 接入 TDengine 后填充实际数据
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            OffsetDateTime day = OffsetDateTime.now().minusDays(i);
            trend.add(Map.of(
                    "time", day.toLocalDate().toString(),
                    "count", 0,
                    "alarms", 0,
                    "avg_latency_ms", 0.0
            ));
        }
        return ApiResponse.ok(trend);
    }

    /**
     * 内部调用：广播实时告警到所有 SSE 客户端（由 alarm-judge-service 通过 Kafka 消费后触发）
     */
    public void broadcastAlarm(String alarmJson) {
        sseClients.removeIf(emitter -> {
            try {
                emitter.send(SseEmitter.event().name("alarm").data(alarmJson));
                return false;
            } catch (Exception e) {
                return true; // 发送失败，移除客户端
            }
        });
    }
}
