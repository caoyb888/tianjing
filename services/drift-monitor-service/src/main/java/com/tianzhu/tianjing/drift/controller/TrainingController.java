package com.tianzhu.tianjing.drift.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianzhu.tianjing.common.response.ApiResponse;
import com.tianzhu.tianjing.common.response.PageResult;
import com.tianzhu.tianjing.common.security.TianjingUserDetails;
import com.tianzhu.tianjing.drift.domain.Dataset;
import com.tianzhu.tianjing.drift.domain.TrainJob;
import com.tianzhu.tianjing.drift.dto.DatasetDTO;
import com.tianzhu.tianjing.drift.dto.DatasetVersionItem;
import com.tianzhu.tianjing.drift.dto.TrainJobRequest;
import com.tianzhu.tianjing.drift.repository.DatasetMapper;
import com.tianzhu.tianjing.drift.repository.TrainJobMapper;
import com.tianzhu.tianjing.drift.training.DockerTrainJobLauncher;
import com.tianzhu.tianjing.common.exception.BusinessException;
import com.tianzhu.tianjing.common.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 训练管理接口
 * 规范：API 接口规范 V3.1 §6.10（5 个端点）
 * 注意：接口只访问 tianjing_train 库（通过 Flyway train 迁移建立）
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/training")
@RequiredArgsConstructor
public class TrainingController {

    private final TrainJobMapper trainJobMapper;
    private final DatasetMapper datasetMapper;
    private final DockerTrainJobLauncher dockerTrainJobLauncher;

    @GetMapping("/jobs")
    public ApiResponse<PageResult<TrainJob>> listJobs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String plugin_id,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String trigger_type) {
        Page<TrainJob> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<TrainJob> wrapper = new LambdaQueryWrapper<TrainJob>()
                .eq(plugin_id != null, TrainJob::getPluginId, plugin_id)
                .eq(status != null, TrainJob::getStatus, status)
                .eq(trigger_type != null, TrainJob::getTriggerType, trigger_type)
                .orderByDesc(TrainJob::getCreatedAt);
        var result = trainJobMapper.selectPage(pageParam, wrapper);
        return ApiResponse.page(PageResult.of(result.getTotal(), page, size, result.getRecords()));
    }

    @GetMapping("/jobs/{job_id}")
    public ApiResponse<TrainJob> getJob(@PathVariable("job_id") String jobId) {
        TrainJob job = trainJobMapper.selectOne(new LambdaQueryWrapper<TrainJob>()
                .eq(TrainJob::getJobId, jobId));
        if (job == null) throw BusinessException.notFound(ErrorCode.TRAIN_JOB_NOT_FOUND);
        return ApiResponse.ok(job);
    }

    @PostMapping("/jobs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<TrainJob> submitJob(
            @Valid @RequestBody TrainJobRequest request,
            @AuthenticationPrincipal TianjingUserDetails user) {
        TrainJob job = new TrainJob();
        job.setJobId("TJ-" + request.pluginId() + "-" + System.currentTimeMillis());
        job.setPluginId(request.pluginId());
        job.setDatasetVersionId(request.datasetVersionId());
        job.setGpuCount(request.gpuCount() != null ? request.gpuCount() : 1);
        job.setTriggerType("MANUAL");
        job.setStatus("PENDING");
        job.setCreatedBy(user.getUsername());
        try {
            job.setTrainConfigJson(new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(request.trainConfigJson()));
        } catch (Exception e) {
            job.setTrainConfigJson("{}");
        }
        trainJobMapper.insert(job);
        log.info("提交训练作业 job_id={} plugin_id={} operator={}", job.getJobId(), job.getPluginId(), user.getUsername());

        // 启动训练容器（docker run）；若容器启动失败，立即将作业置为 FAILED
        try {
            dockerTrainJobLauncher.launch(job);
        } catch (Exception e) {
            log.error("训练容器启动失败，将作业置为 FAILED job_id={}", job.getJobId(), e);
            job.setStatus("FAILED");
            job.setFinishedAt(OffsetDateTime.now());
            job.setErrorMsg("训练容器启动失败: " + e.getMessage());
            trainJobMapper.updateById(job);
        }

        return ApiResponse.ok(job);
    }

    @PostMapping("/jobs/{job_id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> cancelJob(
            @PathVariable("job_id") String jobId,
            @AuthenticationPrincipal TianjingUserDetails user) {
        TrainJob job = trainJobMapper.selectOne(new LambdaQueryWrapper<TrainJob>()
                .eq(TrainJob::getJobId, jobId));
        if (job == null) throw BusinessException.notFound(ErrorCode.TRAIN_JOB_NOT_FOUND);
        if ("COMPLETED".equals(job.getStatus()) || "FAILED".equals(job.getStatus())) {
            throw BusinessException.of(ErrorCode.TRAIN_JOB_CANCEL_FORBIDDEN);
        }
        job.setStatus("CANCELLED");
        trainJobMapper.updateById(job);
        log.info("取消训练作业 job_id={} operator={}", jobId, user.getUsername());
        return ApiResponse.ok();
    }

    @GetMapping("/datasets")
    public ApiResponse<PageResult<DatasetDTO>> listDatasets(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String factory,
            @RequestParam(required = false) String keyword) {
        Page<Dataset> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Dataset> wrapper = new LambdaQueryWrapper<Dataset>()
                .eq(Dataset::getIsDeleted, false)
                .eq(factory != null, Dataset::getFactoryCode, toDbFactoryCode(factory))
                .like(keyword != null, Dataset::getDatasetName, keyword)
                .orderByDesc(Dataset::getCreatedAt);
        var result = datasetMapper.selectPage(pageParam, wrapper);
        var items = result.getRecords().stream()
                .map(d -> DatasetDTO.of(d, datasetMapper.selectVersionTags(d.getDatasetCode())))
                .toList();
        return ApiResponse.page(PageResult.of(result.getTotal(), page, size, items));
    }

    /** 供提交训练作业表单下拉选择：返回全部可用数据集版本（含 version_id） */
    @GetMapping("/dataset-versions")
    public ApiResponse<List<DatasetVersionItem>> listDatasetVersions() {
        return ApiResponse.ok(datasetMapper.selectAllVersions());
    }

    @GetMapping("/datasets/{dataset_code}")
    public ApiResponse<DatasetDTO> getDataset(@PathVariable("dataset_code") String datasetCode) {
        Dataset dataset = datasetMapper.selectOne(new LambdaQueryWrapper<Dataset>()
                .eq(Dataset::getDatasetCode, datasetCode)
                .eq(Dataset::getIsDeleted, false));
        if (dataset == null) throw BusinessException.notFound(ErrorCode.SCENE_NOT_FOUND);
        var versions = datasetMapper.selectVersionTags(datasetCode);
        return ApiResponse.ok(DatasetDTO.of(dataset, versions));
    }

    /** 前端 Factory 枚举值（小写）→ DB factory_code（大写），与 DatasetDTO.normalizeFactory 互为反向 */
    private static String toDbFactoryCode(String frontendValue) {
        if (frontendValue == null) return null;
        return switch (frontendValue.toLowerCase()) {
            case "pellet"    -> "PELLET";
            case "sintering" -> "SINTER";
            case "steel"     -> "STEEL";
            case "section"   -> "SECTION";
            case "strip"     -> "STRIP";
            default          -> frontendValue.toUpperCase();
        };
    }
}
