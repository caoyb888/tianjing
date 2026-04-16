package com.tianzhu.tianjing.dashboard.controller;

import com.tianzhu.tianjing.common.response.ApiResponse;
import com.tianzhu.tianjing.dashboard.dto.FactorySummaryDTO;
import com.tianzhu.tianjing.dashboard.dto.OverviewStatsDTO;
import com.tianzhu.tianjing.dashboard.dto.TrendPointDTO;
import com.tianzhu.tianjing.dashboard.service.DashboardService;
import com.tianzhu.tianjing.dashboard.service.LiveStreamService;
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
 * 扩展：实时告警大屏开发优化计划.md Phase 1（新增筛选参数和厂部汇总接口）
 * 包含：概览统计 / SSE 实时告警 / 推理趋势 / 厂部汇总
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final LiveStreamService liveStreamService;

    /** SSE 客户端列表（线程安全） */
    private final List<SseEmitter> sseClients = new CopyOnWriteArrayList<>();

    /**
     * GET /dashboard/overview — 平台概览统计
     * 字段名与前端 DashboardView stats 计算属性对齐
     *
     * @param factory 厂部编码（可选）：PELLET/SINTER/STEEL/SECTION/STRIP
     * @param sceneId 场景ID（可选）
     */
    @GetMapping("/overview")
    public ApiResponse<OverviewStatsDTO> overview(
            @RequestParam(required = false) String factory,
            @RequestParam(required = false) String sceneId) {
        log.debug("查询概览统计 factory={} sceneId={}", factory, sceneId);
        OverviewStatsDTO stats = dashboardService.getOverview(factory, sceneId);
        return ApiResponse.ok(stats);
    }

    /**
     * GET /dashboard/factory-summary — 各厂部汇总统计（热力柱图）
     * 规范：实时告警大屏开发优化计划.md §3.3
     * 返回5个厂部的今日告警、活跃场景、在线设备、今日推理量
     */
    @GetMapping("/factory-summary")
    public ApiResponse<List<FactorySummaryDTO>> factorySummary() {
        log.debug("查询厂部汇总统计");
        List<FactorySummaryDTO> summary = dashboardService.getFactorySummary();
        return ApiResponse.ok(summary);
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
     *
     * @param days    天数（默认7天）
     * @param factory 厂部编码（可选）
     * @param sceneId 场景ID（可选）
     */
    @GetMapping("/inference-trend")
    public ApiResponse<List<TrendPointDTO>> inferenceTrend(
            @RequestParam(defaultValue = "7") int days,
            @RequestParam(required = false) String factory,
            @RequestParam(required = false) String sceneId) {
        log.debug("查询推理趋势 days={} factory={} sceneId={}", days, factory, sceneId);
        List<TrendPointDTO> trend = dashboardService.getInferenceTrend(days, factory, sceneId);
        return ApiResponse.ok(trend);
    }

    /**
     * GET /dashboard/infer/live-stream — 实时推理帧 SSE 流（大屏实时推理画面）
     * 前端使用 EventSource 订阅，每帧触发一次 "frame" 事件。
     * 事件数据包含 image_url（minio:// 格式）和 detections，供大屏 Canvas 渲染。
     *
     * 规范：CLAUDE.md §11.1（Sandbox 帧在 LiveStreamService 消费时已过滤）
     */
    @GetMapping(value = "/infer/live-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter liveStream() {
        SseEmitter emitter = liveStreamService.subscribe();
        // 发送连接确认
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data("{\"message\":\"实时推理流已建立\"}"));
        } catch (Exception ignored) {}
        return emitter;
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
