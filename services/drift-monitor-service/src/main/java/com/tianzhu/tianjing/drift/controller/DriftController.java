package com.tianzhu.tianjing.drift.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianzhu.tianjing.common.response.ApiResponse;
import com.tianzhu.tianjing.common.security.TianjingUserDetails;
import com.tianzhu.tianjing.drift.domain.DriftMetric;
import com.tianzhu.tianjing.drift.repository.DriftMetricMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 模型漂移监测接口
 * 规范：API 接口规范 V3.1 §6.11（2 个端点）
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/drift")
@RequiredArgsConstructor
public class DriftController {

    private final DriftMetricMapper driftMetricMapper;

    /**
     * GET /drift/metrics — 精度漂移趋势与场景汇总
     * 响应结构：{ trend: [{date, precision, recall}], scenes: [{scene_id, precision, recall, below_threshold_days, is_drifting}] }
     */
    @GetMapping("/metrics")
    public ApiResponse<Map<String, Object>> listMetrics(
            @RequestParam(required = false) String sceneId,
            @RequestParam(defaultValue = "30") int days) {

        LocalDate fromDate = LocalDate.now().minusDays(days);

        // 查询最近 N 天的全部漂移指标
        LambdaQueryWrapper<DriftMetric> wrapper = new LambdaQueryWrapper<DriftMetric>()
                .eq(sceneId != null && !sceneId.isBlank(), DriftMetric::getSceneId, sceneId)
                .ge(DriftMetric::getMetricDate, fromDate)
                .orderByAsc(DriftMetric::getMetricDate);
        List<DriftMetric> metrics = driftMetricMapper.selectList(wrapper);

        // trend：按日期聚合（多场景时取均值），供折线图使用
        List<Map<String, Object>> trend = metrics.stream()
                .collect(Collectors.groupingBy(DriftMetric::getMetricDate))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    List<DriftMetric> dayMetrics = e.getValue();
                    double avgPrecision = dayMetrics.stream()
                            .filter(m -> m.getPrecisionVal() != null)
                            .mapToDouble(DriftMetric::getPrecisionVal).average().orElse(0);
                    double avgRecall = dayMetrics.stream()
                            .filter(m -> m.getRecallVal() != null)
                            .mapToDouble(DriftMetric::getRecallVal).average().orElse(0);
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("date", e.getKey().toString());
                    point.put("precision", Math.round(avgPrecision * 10000.0) / 10000.0);
                    point.put("recall", Math.round(avgRecall * 10000.0) / 10000.0);
                    return point;
                })
                .toList();

        // scenes：每个场景取最新一条指标，汇总状态
        List<Map<String, Object>> scenes = metrics.stream()
                .collect(Collectors.groupingBy(DriftMetric::getSceneId))
                .entrySet().stream()
                .map(e -> {
                    // 最新一条
                    DriftMetric latest = e.getValue().stream()
                            .max(Comparator.comparing(DriftMetric::getMetricDate)).orElseThrow();
                    // 连续低于阈值天数（取最新记录的 consecutiveBelow）
                    int belowDays = latest.getConsecutiveBelow() != null ? latest.getConsecutiveBelow() : 0;
                    boolean isDrifting = belowDays >= 3;

                    Map<String, Object> scene = new LinkedHashMap<>();
                    scene.put("scene_id", e.getKey());
                    scene.put("precision", latest.getPrecisionVal());
                    scene.put("recall", latest.getRecallVal());
                    scene.put("below_threshold_days", belowDays);
                    scene.put("is_drifting", isDrifting);
                    scene.put("retrain_triggered", Boolean.TRUE.equals(latest.getRetrainTriggered()));
                    return scene;
                })
                .sorted(Comparator.comparing(s -> s.get("scene_id").toString()))
                .toList();

        return ApiResponse.ok(Map.of("trend", trend, "scenes", scenes));
    }

    /**
     * POST /drift/retrain — 手动触发重训练
     * 前端路径 /drift/retrain，保留 /drift/retrain-trigger 兼容旧调用
     */
    @PostMapping({"/retrain", "/retrain-trigger"})
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> triggerRetrain(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal TianjingUserDetails user) {
        String scene = request.getOrDefault("sceneId", request.get("scene_id"));
        String reason = request.getOrDefault("reason", "手动触发重训练");
        log.info("手动触发重训练 scene_id={} operator={} reason={}", scene, user.getUsername(), reason);
        return ApiResponse.ok(Map.of(
                "message", "重训练任务已提交",
                "scene_id", scene != null ? scene : "",
                "trigger_type", "MANUAL",
                "operator", user.getUsername()
        ));
    }
}
