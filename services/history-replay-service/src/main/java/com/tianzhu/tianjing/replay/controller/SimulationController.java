package com.tianzhu.tianjing.replay.controller;

import com.tianzhu.tianjing.common.response.ApiResponse;
import com.tianzhu.tianjing.common.response.PageResult;
import com.tianzhu.tianjing.common.security.TianjingUserDetails;
import com.tianzhu.tianjing.replay.domain.SimulationTask;
import com.tianzhu.tianjing.replay.domain.SimulationVideo;
import com.tianzhu.tianjing.replay.dto.*;
import com.tianzhu.tianjing.replay.service.AnnotationReviewService;
import com.tianzhu.tianjing.replay.service.DatasetExportService;
import com.tianzhu.tianjing.replay.service.InferenceClient;
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
    private final InferenceClient inferenceClient;
    private final AnnotationReviewService annotationReviewService;

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

    /**
     * GET /simulations/model-status — 查询推理模型加载状态
     * 前端新建任务前调用，判断是否需要预热
     */
    @GetMapping("/model-status")
    public ApiResponse<InferenceClient.ModelHealth> modelStatus() {
        return ApiResponse.ok(inferenceClient.getHealth());
    }

    /**
     * POST /simulations/warmup-model — 预热推理模型（同步，等待加载完成）
     * 发送 1×1 占位图像触发懒加载，最长等待 120s，返回最新健康状态
     */
    @PostMapping("/warmup-model")
    public ApiResponse<InferenceClient.ModelHealth> warmupModel() {
        InferenceClient.ModelHealth health = inferenceClient.warmup();
        return ApiResponse.ok(health);
    }

    // ============================================================================
    // 标注审核接口（标注审核工具开发计划 V1.0）
    // 路径前缀：/api/v1/simulations/{task_id}/review
    // ============================================================================

    /**
     * POST /simulations/{task_id}/review/init — 初始化审核记录
     * 幂等：若已存在记录则跳过，不覆盖
     */
    @PostMapping("/{task_id}/review/init")
    public ApiResponse<AnnotationReviewService.InitReviewResult> initReview(
            @PathVariable("task_id") String taskId) {
        return ApiResponse.ok(annotationReviewService.initReview(taskId));
    }

    /**
     * GET /simulations/{task_id}/review/stats — 审核进度统计
     * 返回：总帧数 / 已审核 / 已通过 / 已拒绝 / 待审 / 进度百分比
     */
    @GetMapping("/{task_id}/review/stats")
    public ApiResponse<ReviewStatsDTO> getReviewStats(
            @PathVariable("task_id") String taskId) {
        return ApiResponse.ok(annotationReviewService.getStats(taskId));
    }

    /**
     * GET /simulations/{task_id}/review/frames — 分页获取帧列表（带审核状态）
     * 用于缩略图导航
     */
    @GetMapping("/{task_id}/review/frames")
    public ApiResponse<PageResult<FrameListItemDTO>> listReviewFrames(
            @PathVariable("task_id") String taskId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        return ApiResponse.page(annotationReviewService.listFrames(taskId, page, size, status));
    }

    /**
     * GET /simulations/{task_id}/review/frames/{frame_id} — 获取单帧详情
     * 含原始标注 + 校正标注 + 图像URL
     */
    @GetMapping("/{task_id}/review/frames/{frame_id}")
    public ApiResponse<FrameDetailDTO> getReviewFrame(
            @PathVariable("task_id") String taskId,
            @PathVariable("frame_id") String frameId) {
        return ApiResponse.ok(annotationReviewService.getFrame(taskId, frameId));
    }

    /**
     * PUT /simulations/{task_id}/review/frames/{frame_id} — 保存单帧校正结果
     * 标注框 + 审核状态
     */
    @PutMapping("/{task_id}/review/frames/{frame_id}")
    public ApiResponse<Void> saveReviewFrame(
            @PathVariable("task_id") String taskId,
            @PathVariable("frame_id") String frameId,
            @Valid @RequestBody SaveFrameRequest request,
            @AuthenticationPrincipal TianjingUserDetails user) {
        annotationReviewService.saveFrame(taskId, frameId, request, user.getUsername());
        return ApiResponse.ok();
    }

    /**
     * POST /simulations/{task_id}/review/bulk-approve — 批量通过
     * 支持按 frame_ids 列表或"全部未改动帧"批量通过
     */
    @PostMapping("/{task_id}/review/bulk-approve")
    public ApiResponse<Map<String, Object>> bulkApprove(
            @PathVariable("task_id") String taskId,
            @Valid @RequestBody BulkApproveRequest request,
            @AuthenticationPrincipal TianjingUserDetails user) {
        int count = annotationReviewService.bulkApprove(taskId, request, user.getUsername());
        return ApiResponse.ok(Map.of(
                "processed_count", count,
                "mode", request.mode()
        ));
    }

    /** POST /simulations/{task_id}/videos 请求体 */
    record AddVideoRequest(
            @NotBlank @JsonProperty("video_url") String videoUrl,
            /** NORMAL / ABNORMAL / MIXED，默认 MIXED */
            @JsonProperty("label") String label
    ) {}
}
