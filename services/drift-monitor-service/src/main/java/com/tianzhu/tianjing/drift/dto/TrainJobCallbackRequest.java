package com.tianzhu.tianjing.drift.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 训练脚本回调请求体（POST /internal/training/jobs/{job_id}/callback）
 *
 * 训练容器在以下时机调用此接口：
 *   RUNNING   — 训练开始时（设置 started_at）
 *   RUNNING   — 每 epoch 完成（更新 best_map50，供前端进度展示）
 *   COMPLETED — 训练完成（写入 mlflow_run_id + 最终指标 + finished_at）
 *   FAILED    — 训练失败（写入 error_msg + finished_at）
 */
public record TrainJobCallbackRequest(
        /** 新状态：RUNNING / COMPLETED / FAILED */
        String status,

        @JsonProperty("mlflow_run_id")
        String mlflowRunId,

        @JsonProperty("best_map50")
        Double bestMap50,

        @JsonProperty("best_map50_95")
        Double bestMap5095,

        @JsonProperty("best_epoch")
        Integer bestEpoch,

        @JsonProperty("error_msg")
        String errorMsg,

        /** 训练完成后自动注册的模型版本 ID，COMPLETED 回调时写入 */
        @JsonProperty("model_version_id")
        String modelVersionId
) {}
