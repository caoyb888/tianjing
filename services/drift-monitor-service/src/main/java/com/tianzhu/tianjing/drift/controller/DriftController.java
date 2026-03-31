package com.tianzhu.tianjing.drift.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianzhu.tianjing.common.response.ApiResponse;
import com.tianzhu.tianjing.common.response.PageResult;
import com.tianzhu.tianjing.common.security.TianjingUserDetails;
import com.tianzhu.tianjing.drift.domain.DriftMetric;
import com.tianzhu.tianjing.drift.repository.DriftMetricMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
     * GET /drift/metrics — 精度漂移趋势查询
     */
    @GetMapping("/metrics")
    public ApiResponse<PageResult<DriftMetric>> listMetrics(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(required = false) String scene_id) {
        Page<DriftMetric> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<DriftMetric> wrapper = new LambdaQueryWrapper<DriftMetric>()
                .eq(scene_id != null, DriftMetric::getSceneId, scene_id)
                .orderByDesc(DriftMetric::getMetricDate);
        var result = driftMetricMapper.selectPage(pageParam, wrapper);
        return ApiResponse.page(PageResult.of(result.getTotal(), page, size, result.getRecords()));
    }

    /**
     * POST /drift/retrain-trigger — 手动触发重训练
     */
    @PostMapping("/retrain-trigger")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Map<String, Object>> triggerRetrain(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal TianjingUserDetails user) {
        String sceneId = request.get("scene_id");
        String reason = request.getOrDefault("reason", "手动触发重训练");
        log.info("手动触发重训练 scene_id={} operator={} reason={}", sceneId, user.getUsername(), reason);
        // 实际训练 K8s Job 创建逻辑（Sprint 3 完善）
        return ApiResponse.ok(Map.of(
                "message", "重训练任务已提交",
                "scene_id", sceneId,
                "trigger_type", "MANUAL",
                "operator", user.getUsername()
        ));
    }
}
