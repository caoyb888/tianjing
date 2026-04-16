package com.tianzhu.tianjing.algomodel.controller;

import com.tianzhu.tianjing.algomodel.domain.AlgorithmPlugin;
import com.tianzhu.tianjing.algomodel.dto.AlgorithmPluginDetail;
import com.tianzhu.tianjing.algomodel.dto.PluginHealthResult;
import com.tianzhu.tianjing.algomodel.service.AlgorithmPluginService;
import com.tianzhu.tianjing.algomodel.service.PluginHealthChecker;
import com.tianzhu.tianjing.common.response.ApiResponse;
import com.tianzhu.tianjing.common.response.PageResult;
import com.tianzhu.tianjing.common.security.TianjingUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 算法插件管理接口
 * 规范：API 接口规范 V3.1 §6.5（3 个端点）
 */
@RestController
@RequestMapping("/api/v1/plugins")
@RequiredArgsConstructor
public class AlgorithmPluginController {

    private final AlgorithmPluginService pluginService;
    private final PluginHealthChecker healthChecker;

    @GetMapping
    public ApiResponse<PageResult<AlgorithmPluginDetail>> listPlugins(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String plugin_type,
            @RequestParam(required = false) String status) {
        return ApiResponse.page(pluginService.listPlugins(page, size, plugin_type, status));
    }

    @GetMapping("/{plugin_id}")
    public ApiResponse<AlgorithmPluginDetail> getPlugin(@PathVariable("plugin_id") String pluginId) {
        return ApiResponse.ok(pluginService.getPlugin(pluginId));
    }

    /**
     * GET /plugins/{plugin_id}/health-check — 算法可用性探测
     * 向推理代理发送 64×64 测试图，验证推理链路是否畅通
     */
    @GetMapping("/{plugin_id}/health-check")
    public ApiResponse<PluginHealthResult> healthCheck(@PathVariable("plugin_id") String pluginId) {
        AlgorithmPluginDetail plugin = pluginService.getPlugin(pluginId);
        // 取第一个支持场景作为推理上下文；无则使用 HEALTH-CHECK
        String sceneId = (plugin.supportedScenes() != null && !plugin.supportedScenes().isEmpty())
                ? plugin.supportedScenes().get(0)
                : "HEALTH-CHECK";
        return ApiResponse.ok(healthChecker.check(pluginId, sceneId, plugin.serviceEndpoint()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<AlgorithmPlugin> registerPlugin(
            @Valid @RequestBody AlgorithmPlugin request,
            @AuthenticationPrincipal TianjingUserDetails user) {
        return ApiResponse.ok(pluginService.registerPlugin(request, user.getUsername()));
    }
}
