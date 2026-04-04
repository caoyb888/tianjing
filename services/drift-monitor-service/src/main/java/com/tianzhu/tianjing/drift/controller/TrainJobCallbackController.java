package com.tianzhu.tianjing.drift.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianzhu.tianjing.common.exception.BusinessException;
import com.tianzhu.tianjing.common.exception.ErrorCode;
import com.tianzhu.tianjing.common.response.ApiResponse;
import com.tianzhu.tianjing.drift.domain.TrainJob;
import com.tianzhu.tianjing.drift.dto.TrainJobCallbackRequest;
import com.tianzhu.tianjing.drift.repository.TrainJobMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

/**
 * 训练状态回调接口（内部接口）
 *
 * 路径 /internal/** 在 CommonSecurityConfig 中 permitAll，
 * 由 K8s NetworkPolicy / mTLS 保护，不走 JWT。
 * 测试环境（3 台服务器）通过防火墙限制外部访问。
 *
 * 规范：CLAUDE.md §10.3
 */
@Slf4j
@RestController
@RequestMapping("/internal/training/jobs")
@RequiredArgsConstructor
public class TrainJobCallbackController {

    private final TrainJobMapper trainJobMapper;

    /**
     * POST /internal/training/jobs/{job_id}/callback
     * 训练容器汇报状态变化。
     */
    @PostMapping("/{job_id}/callback")
    public ApiResponse<Void> callback(
            @PathVariable("job_id") String jobId,
            @RequestBody TrainJobCallbackRequest req) {

        TrainJob job = trainJobMapper.selectOne(
                new LambdaQueryWrapper<TrainJob>().eq(TrainJob::getJobId, jobId));
        if (job == null) throw BusinessException.notFound(ErrorCode.TRAIN_JOB_NOT_FOUND);

        switch (req.status()) {
            case "RUNNING" -> {
                // 首次回调 RUNNING 时设置 started_at，后续 epoch 更新仅刷新指标
                if (job.getStartedAt() == null) {
                    job.setStartedAt(OffsetDateTime.now());
                }
                job.setStatus("RUNNING");
                if (req.bestMap50() != null) job.setBestMap50(req.bestMap50());
                if (req.bestEpoch()  != null) job.setBestEpoch(req.bestEpoch());
                log.debug("训练进度更新 job_id={} epoch={} map50={}",
                        jobId, req.bestEpoch(), req.bestMap50());
            }
            case "COMPLETED" -> {
                job.setStatus("COMPLETED");
                job.setFinishedAt(OffsetDateTime.now());
                if (req.mlflowRunId() != null) job.setMlflowRunId(req.mlflowRunId());
                if (req.bestMap50()   != null) job.setBestMap50(req.bestMap50());
                if (req.bestMap5095() != null) job.setBestMap5095(req.bestMap5095());
                if (req.bestEpoch()   != null) job.setBestEpoch(req.bestEpoch());
                log.info("训练作业完成 job_id={} mlflow_run_id={} best_map50={}",
                        jobId, req.mlflowRunId(), req.bestMap50());
            }
            case "FAILED" -> {
                job.setStatus("FAILED");
                job.setFinishedAt(OffsetDateTime.now());
                if (req.errorMsg() != null) job.setErrorMsg(req.errorMsg());
                log.warn("训练作业失败 job_id={} error={}", jobId, req.errorMsg());
            }
            default -> {
                log.warn("训练回调收到未知状态 job_id={} status={}", jobId, req.status());
                return ApiResponse.ok();
            }
        }

        trainJobMapper.updateById(job);
        return ApiResponse.ok();
    }
}
