package com.tianzhu.tianjing.dashboard.controller;

import com.tianzhu.tianjing.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
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
@RequiredArgsConstructor
public class DashboardController {

    /** SSE 客户端列表（线程安全） */
    private final List<SseEmitter> sseClients = new CopyOnWriteArrayList<>();

    /**
     * GET /dashboard/overview — 平台概览统计
     * 字段名与前端 DashboardView stats 计算属性对齐
     */
    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        Map<String, Object> data = new LinkedHashMap<>();
        // 统计卡片字段（前端 stats computed 使用）
        data.put("active_scenes", 1);
        data.put("online_devices", 0);
        data.put("today_alarms", 0);
        data.put("today_inferences", 0);
        // 告警饼图字段（AlarmHeatmapChart 使用）
        data.put("critical_alarms", 0);
        data.put("warning_alarms", 0);
        data.put("info_alarms", 0);
        // 附加字段
        data.put("total_scenes", 16);
        data.put("avg_infer_latency_ms", 0.0);
        data.put("sandbox_sessions_running", 0);
        data.put("factories", List.of(
                Map.of("factory_code", "SINTER", "active_scenes", 1, "alarms_today", 0),
                Map.of("factory_code", "STEEL",  "active_scenes", 0, "alarms_today", 0),
                Map.of("factory_code", "PELLET", "active_scenes", 0, "alarms_today", 0),
                Map.of("factory_code", "SECTION","active_scenes", 0, "alarms_today", 0),
                Map.of("factory_code", "STRIP",  "active_scenes", 0, "alarms_today", 0)
        ));
        data.put("generated_at", OffsetDateTime.now());
        return ApiResponse.ok(data);
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
