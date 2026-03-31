package com.tianzhu.tianjing.route.controller;

import com.tianzhu.tianjing.common.ApiResponse;
import com.tianzhu.tianjing.route.service.RouteConfigCache;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 路由管理内部接口（仅内部可达，NetworkPolicy 限制外部访问）
 *
 * 规范：CLAUDE.md §4.2
 */
@RestController
@RequestMapping("/internal/route")
@RequiredArgsConstructor
public class RouteManageController {

    private final RouteConfigCache routeConfigCache;

    /**
     * 获取当前所有激活路由快照
     * GET /internal/route/active
     */
    @GetMapping("/active")
    public ApiResponse<Object> getActiveRoutes() {
        return ApiResponse.ok(routeConfigCache.getActiveRoutes());
    }

    /**
     * 强制刷新指定场景路由（从 Redis 重新拉取）
     * POST /internal/route/{sceneId}/refresh
     */
    @PostMapping("/{sceneId}/refresh")
    public ApiResponse<Void> refreshRoute(@PathVariable String sceneId) {
        routeConfigCache.forceRefresh(sceneId);
        return ApiResponse.ok();
    }
}
