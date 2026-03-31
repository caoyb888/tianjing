package com.tianzhu.tianjing.replay.controller;

import com.tianzhu.tianjing.common.response.ApiResponse;
import com.tianzhu.tianjing.common.response.PageResult;
import com.tianzhu.tianjing.common.security.TianjingUserDetails;
import com.tianzhu.tianjing.replay.domain.SimulationTask;
import com.tianzhu.tianjing.replay.dto.SimulationCreateRequest;
import com.tianzhu.tianjing.replay.service.SimulationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 离线仿真接口
 * 规范：API 接口规范 V3.1 §6.9（5 个端点）
 */
@RestController
@RequestMapping("/api/v1/simulation")
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationService simulationService;

    @GetMapping("/tasks")
    public ApiResponse<PageResult<SimulationTask>> listTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String scene_id) {
        return ApiResponse.page(simulationService.listTasks(page, size, scene_id));
    }

    /** GET /simulation/upload-url — 获取 MinIO 预签名上传 URL（1h 有效） */
    @GetMapping("/upload-url")
    public ApiResponse<Map<String, Object>> getUploadUrl(
            @RequestParam String scene_id,
            @RequestParam String file_name,
            @AuthenticationPrincipal TianjingUserDetails user) {
        return ApiResponse.ok(simulationService.getUploadUrl(scene_id, file_name, user.getUsername()));
    }

    /** POST /simulation/tasks — 创建仿真任务（视频已上传 MinIO 后调用） */
    @PostMapping("/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SimulationTask> createTask(
            @Valid @RequestBody SimulationCreateRequest request,
            @AuthenticationPrincipal TianjingUserDetails user) {
        return ApiResponse.ok(simulationService.createTask(request, user.getUsername()));
    }

    /** GET /simulation/tasks/{task_id} — 查询仿真进度 */
    @GetMapping("/tasks/{task_id}")
    public ApiResponse<SimulationTask> getProgress(
            @PathVariable("task_id") String taskId) {
        return ApiResponse.ok(simulationService.getTaskProgress(taskId));
    }

    /** POST /simulation/tasks/{task_id}/cancel — 取消仿真任务（接口 #43） */
    @PostMapping("/tasks/{task_id}/cancel")
    public ApiResponse<Void> cancelTask(
            @PathVariable("task_id") String taskId,
            @AuthenticationPrincipal TianjingUserDetails user) {
        simulationService.cancelTask(taskId, user.getUsername());
        return ApiResponse.ok();
    }
}
