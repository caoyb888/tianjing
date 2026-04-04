package com.tianzhu.tianjing.drift.controller;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tianzhu.tianjing.common.exception.BusinessException;
import com.tianzhu.tianjing.drift.domain.TrainJob;
import com.tianzhu.tianjing.drift.dto.TrainJobCallbackRequest;
import com.tianzhu.tianjing.drift.repository.TrainJobMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TrainJobCallbackController 单元测试
 *
 * 覆盖：RUNNING / COMPLETED / FAILED 状态回写、model_version_id 写入、
 *       未知状态忽略、job 不存在抛 404。
 * 规范：CLAUDE.md §12.1（其他后端服务覆盖率 ≥ 80%）
 * 对应任务：T-04 训练状态回写、T-06 model_version_id 字段写入
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TrainJobCallbackController — 训练状态回写")
class TrainJobCallbackControllerTest {

    @Mock
    private TrainJobMapper trainJobMapper;

    @InjectMocks
    private TrainJobCallbackController controller;

    private TrainJob pendingJob;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), TrainJob.class);

        pendingJob = new TrainJob();
        pendingJob.setJobId("TJ-PLUGIN-001-1743500000000");
        pendingJob.setPluginId("ATOM-DETECT-YOLO-V1");
        pendingJob.setStatus("PENDING");
        pendingJob.setCreatedAt(OffsetDateTime.now().minusMinutes(10));
    }

    // ── RUNNING ──────────────────────────────────────────────

    @Test
    @DisplayName("RUNNING 首次回调：状态变为 RUNNING，startedAt 被设置")
    void callback_running_firstTime_setsStartedAt() {
        when(trainJobMapper.selectOne(any())).thenReturn(pendingJob);

        TrainJobCallbackRequest req = new TrainJobCallbackRequest(
                "RUNNING", null, 0.1234, null, 3, null, null);
        controller.callback(pendingJob.getJobId(), req);

        ArgumentCaptor<TrainJob> captor = ArgumentCaptor.forClass(TrainJob.class);
        verify(trainJobMapper).updateById(captor.capture());
        TrainJob saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo("RUNNING");
        assertThat(saved.getStartedAt()).isNotNull();
        assertThat(saved.getBestMap50()).isEqualTo(0.1234);
        assertThat(saved.getBestEpoch()).isEqualTo(3);
    }

    @Test
    @DisplayName("RUNNING 后续 epoch 回调：startedAt 不被重复设置")
    void callback_running_subsequent_doesNotOverwriteStartedAt() {
        OffsetDateTime originalStart = OffsetDateTime.now().minusMinutes(5);
        pendingJob.setStatus("RUNNING");
        pendingJob.setStartedAt(originalStart);
        when(trainJobMapper.selectOne(any())).thenReturn(pendingJob);

        TrainJobCallbackRequest req = new TrainJobCallbackRequest(
                "RUNNING", null, 0.3456, null, 7, null, null);
        controller.callback(pendingJob.getJobId(), req);

        ArgumentCaptor<TrainJob> captor = ArgumentCaptor.forClass(TrainJob.class);
        verify(trainJobMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStartedAt()).isEqualTo(originalStart);
        assertThat(captor.getValue().getBestMap50()).isEqualTo(0.3456);
    }

    // ── COMPLETED ────────────────────────────────────────────

    @Test
    @DisplayName("COMPLETED 回调：状态变为 COMPLETED，所有指标字段正确写入")
    void callback_completed_setsAllMetrics() {
        pendingJob.setStatus("RUNNING");
        pendingJob.setStartedAt(OffsetDateTime.now().minusMinutes(30));
        when(trainJobMapper.selectOne(any())).thenReturn(pendingJob);

        TrainJobCallbackRequest req = new TrainJobCallbackRequest(
                "COMPLETED",
                "mlflow-run-abc123",
                0.8750, 0.7210, 163,
                null, "MV-ABCD1234");

        controller.callback(pendingJob.getJobId(), req);

        ArgumentCaptor<TrainJob> captor = ArgumentCaptor.forClass(TrainJob.class);
        verify(trainJobMapper).updateById(captor.capture());
        TrainJob saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo("COMPLETED");
        assertThat(saved.getFinishedAt()).isNotNull();
        assertThat(saved.getMlflowRunId()).isEqualTo("mlflow-run-abc123");
        assertThat(saved.getBestMap50()).isEqualTo(0.8750);
        assertThat(saved.getBestMap5095()).isEqualTo(0.7210);
        assertThat(saved.getBestEpoch()).isEqualTo(163);
        assertThat(saved.getModelVersionId()).isEqualTo("MV-ABCD1234");
    }

    @Test
    @DisplayName("COMPLETED 回调：model_version_id 为 null 时不覆盖原值（可选字段）")
    void callback_completed_nullModelVersionId_doesNotOverwrite() {
        pendingJob.setStatus("RUNNING");
        pendingJob.setStartedAt(OffsetDateTime.now().minusMinutes(5));
        when(trainJobMapper.selectOne(any())).thenReturn(pendingJob);

        // 不传 model_version_id
        TrainJobCallbackRequest req = new TrainJobCallbackRequest(
                "COMPLETED", "mlflow-run-xyz", 0.75, null, 10, null, null);

        controller.callback(pendingJob.getJobId(), req);

        ArgumentCaptor<TrainJob> captor = ArgumentCaptor.forClass(TrainJob.class);
        verify(trainJobMapper).updateById(captor.capture());
        assertThat(captor.getValue().getModelVersionId()).isNull();
    }

    // ── FAILED ───────────────────────────────────────────────

    @Test
    @DisplayName("FAILED 回调：状态变为 FAILED，errorMsg 和 finishedAt 正确写入")
    void callback_failed_setsErrorMsgAndFinishedAt() {
        pendingJob.setStatus("RUNNING");
        pendingJob.setStartedAt(OffsetDateTime.now().minusMinutes(10));
        when(trainJobMapper.selectOne(any())).thenReturn(pendingJob);

        TrainJobCallbackRequest req = new TrainJobCallbackRequest(
                "FAILED", null, null, null, null,
                "CUDA out of memory: batch_size=32 exceeds device limit", null);

        controller.callback(pendingJob.getJobId(), req);

        ArgumentCaptor<TrainJob> captor = ArgumentCaptor.forClass(TrainJob.class);
        verify(trainJobMapper).updateById(captor.capture());
        TrainJob saved = captor.getValue();

        assertThat(saved.getStatus()).isEqualTo("FAILED");
        assertThat(saved.getFinishedAt()).isNotNull();
        assertThat(saved.getErrorMsg()).contains("CUDA out of memory");
    }

    // ── 未知状态 ─────────────────────────────────────────────

    @Test
    @DisplayName("未知状态：忽略回调，不更新数据库")
    void callback_unknownStatus_doesNotUpdate() {
        when(trainJobMapper.selectOne(any())).thenReturn(pendingJob);

        TrainJobCallbackRequest req = new TrainJobCallbackRequest(
                "UNKNOWN_STATE", null, null, null, null, null, null);

        controller.callback(pendingJob.getJobId(), req);

        verify(trainJobMapper, never()).updateById(any(TrainJob.class));
    }

    // ── job 不存在 ───────────────────────────────────────────

    @Test
    @DisplayName("job 不存在：抛出 BusinessException（404）")
    void callback_jobNotFound_throwsBusinessException() {
        when(trainJobMapper.selectOne(any())).thenReturn(null);

        TrainJobCallbackRequest req = new TrainJobCallbackRequest(
                "RUNNING", null, null, null, null, null, null);

        assertThatThrownBy(() -> controller.callback("NON-EXISTENT-JOB", req))
                .isInstanceOf(BusinessException.class);

        verify(trainJobMapper, never()).updateById(any(TrainJob.class));
    }
}
