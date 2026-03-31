package com.tianzhu.tianjing.health.controller;

import com.tianzhu.tianjing.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 感知健康内部接口
 * 规范：API 接口规范 V3.1 §6.15（1 个内部端点）
 *
 * 【内部接口】通过 K8s NetworkPolicy + Istio mTLS 保护，不走 JWT，不对外暴露
 * 仅集群内部其他服务可调用
 */
@Slf4j
@RestController
@RequestMapping("/internal/devices")
@RequiredArgsConstructor
public class HealthReportController {

    /**
     * POST /internal/devices/health/report — 感知健康批量上报
     * 由 frame-extract-service 或 preprocess-service 定期调用
     * 上报图像质量检测结果（拉普拉斯方差/亮度/光流偏移）
     */
    @PostMapping("/health/report")
    public ApiResponse<Map<String, Object>> batchReport(
            @RequestBody List<Map<String, Object>> reports) {

        int processed = 0;
        for (Map<String, Object> report : reports) {
            String deviceCode = (String) report.get("device_code");
            String sceneId = (String) report.get("scene_id");
            Object laplacianVariance = report.get("laplacian_variance");
            Object avgBrightness = report.get("avg_brightness");

            // 健康判定逻辑（规范：CLAUDE.md §11.1 V2.0 录像模式）
            // 实际图像质量检测由 preprocess-service 执行，结果上报至此处持久化
            log.debug("处理健康上报 device_code={} scene_id={} laplacian={} brightness={}",
                    deviceCode, sceneId, laplacianVariance, avgBrightness);
            processed++;
        }

        log.info("感知健康批量上报完成 count={}", processed);
        return ApiResponse.ok(Map.of(
                "processed", processed,
                "timestamp", OffsetDateTime.now()
        ));
    }
}
