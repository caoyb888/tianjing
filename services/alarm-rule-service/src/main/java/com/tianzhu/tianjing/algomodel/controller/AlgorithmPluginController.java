package com.tianzhu.tianjing.algomodel.controller;

import com.tianzhu.tianjing.algomodel.domain.AlgorithmPlugin;
import com.tianzhu.tianjing.algomodel.service.AlgorithmPluginService;
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

    @GetMapping
    public ApiResponse<PageResult<AlgorithmPlugin>> listPlugins(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String plugin_type,
            @RequestParam(required = false) String status) {
        return ApiResponse.page(pluginService.listPlugins(page, size, plugin_type, status));
    }

    @GetMapping("/{plugin_id}")
    public ApiResponse<AlgorithmPlugin> getPlugin(@PathVariable("plugin_id") String pluginId) {
        return ApiResponse.ok(pluginService.getPlugin(pluginId));
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
