package com.tianzhu.tianjing.replay.controller;

import com.tianzhu.tianjing.common.response.ApiResponse;
import com.tianzhu.tianjing.common.response.PageResult;
import com.tianzhu.tianjing.common.security.TianjingUserDetails;
import com.tianzhu.tianjing.replay.domain.SimulationTask;
import com.tianzhu.tianjing.replay.domain.SimulationVideo;
import com.tianzhu.tianjing.replay.dto.DatasetExportRequest;
import com.tianzhu.tianjing.replay.dto.DatasetExportStatusDTO;
import com.tianzhu.tianjing.replay.dto.SimulationCreateRequest;
import com.tianzhu.tianjing.replay.service.DatasetExportService;
import com.tianzhu.tianjing.replay.service.SimulationService;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 离线仿真接口
 * 规范：API 接口规范 V3.1 §6.9（5 个端点）
 *
 * 路径对齐说明（修复 BLK-02）：
 *   原路径 /api/v1/simulation（单数）→ /api/v1/simulations（复数，匹配前端）
 *   原路径 /simulation/upload-url（预签名 URL）→ /simulations/upload-video（multipart 直传，匹配前端）
 *   原路径 /simulation/tasks/**（带 /tasks 子路径）→ /simulations/**（直接挂载，匹配前端）
 */
@RestController
@RequestMapping("/api/v1/simulations")
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationService simulationService;
    private final DatasetExportService datasetExportService;

    /** GET /simulations — 仿真任务列表 */
    @GetMapping
    public ApiResponse<PageResult<SimulationTask>> listTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String scene_id) {
        return ApiResponse.page(simulationService.listTasks(page, size, scene_id));
    }

    /** GET /simulations/{task_id} — 查询仿真任务详情/进度 */
    @GetMapping("/{task_id}")
    public ApiResponse<SimulationTask> getTask(
            @PathVariable("task_id") String taskId) {
        return ApiResponse.ok(simulationService.getTaskProgress(taskId));
    }

    /**
     * POST /simulations/upload-video — 上传仿真视频（multipart）
     * 响应：{ url: "http://minio:9000/tianjing-sim-temp/...", object_path: "..." }
     */
    @PostMapping(value = "/upload-video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, String>> uploadVideo(
            @RequestPart("file") MultipartFile file,
            @RequestParam("scene_id") String sceneId,
            @AuthenticationPrincipal TianjingUserDetails user) {
        return ApiResponse.ok(simulationService.uploadVideo(file, sceneId));
    }

    /** POST /simulations — 创建仿真任务（视频已上传后调用） */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SimulationTask> createTask(
            @Valid @RequestBody SimulationCreateRequest request,
            @AuthenticationPrincipal TianjingUserDetails user) {
        return ApiResponse.ok(simulationService.createTask(request, user.getUsername()));
    }

    /**
     * GET /simulations/{task_id}/videos — 查询任务的视频列表
     */
    @GetMapping("/{task_id}/videos")
    public ApiResponse<List<SimulationVideo>> listVideos(
            @PathVariable("task_id") String taskId) {
        return ApiResponse.ok(simulationService.listVideos(taskId));
    }

    /**
     * POST /simulations/{task_id}/videos — 向已有任务追加一个视频
     * 用于"分次上传"场景：任务创建后继续补传视频
     */
    @PostMapping("/{task_id}/videos")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SimulationVideo> addVideo(
            @PathVariable("task_id") String taskId,
            @Valid @RequestBody AddVideoRequest request,
            @AuthenticationPrincipal TianjingUserDetails user) {
        return ApiResponse.ok(simulationService.addVideo(taskId, request.videoUrl(), request.label(), user.getUsername()));
    }

    /** POST /simulations/{task_id}/cancel — 取消仿真任务 */
    @PostMapping("/{task_id}/cancel")
    public ApiResponse<Void> cancelTask(
            @PathVariable("task_id") String taskId,
            @AuthenticationPrincipal TianjingUserDetails user) {
        simulationService.cancelTask(taskId, user.getUsername());
        return ApiResponse.ok();
    }

    /**
     * POST /simulations/{task_id}/export-dataset — 触发导出训练数据集
     * 异步执行，立即返回 export_task_id；通过 export-status 轮询进度
     */
    @PostMapping("/{task_id}/export-dataset")
    public ApiResponse<Map<String, String>> exportDataset(
            @PathVariable("task_id") String taskId,
            @Valid @RequestBody DatasetExportRequest request) {
        SimulationTask task = simulationService.getTask(taskId);
        datasetExportService.startExportAsync(task, request);
        return ApiResponse.ok(Map.of(
                "export_task_id", "EXP-" + taskId,
                "dataset_version_id", request.datasetVersionId()
        ));
    }

    /**
     * GET /simulations/{task_id}/export-status — 查询导出进度
     */
    @GetMapping("/{task_id}/export-status")
    public ApiResponse<DatasetExportStatusDTO> getExportStatus(
            @PathVariable("task_id") String taskId) {
        SimulationTask task = simulationService.getTask(taskId);
        return ApiResponse.ok(datasetExportService.getExportStatus(task));
    }

    /** POST /simulations/{task_id}/videos 请求体 */
    record AddVideoRequest(
            @NotBlank @JsonProperty("video_url") String videoUrl,
            /** NORMAL / ABNORMAL / MIXED，默认 MIXED */
            @JsonProperty("label") String label
    ) {}
}
