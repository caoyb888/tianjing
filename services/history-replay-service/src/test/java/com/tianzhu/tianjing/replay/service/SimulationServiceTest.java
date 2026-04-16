package com.tianzhu.tianjing.replay.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianzhu.tianjing.common.exception.BusinessException;
import com.tianzhu.tianjing.common.exception.ErrorCode;
import com.tianzhu.tianjing.replay.domain.SimulationTask;
import com.tianzhu.tianjing.replay.dto.SimulationCreateRequest;
import com.tianzhu.tianjing.replay.repository.SimulationTaskMapper;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SimulationService 单元测试
 * 覆盖：uploadVideo / createTask / getTaskProgress / cancelTask
 * 规范：CLAUDE.md §12.1，Service 层覆盖率 ≥ 80%；§7.3 MinIO 存储规范
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SimulationService — 仿真服务单元测试")
class SimulationServiceTest {

    @Mock private SimulationTaskMapper taskMapper;
    @Mock private MinioClient minioClient;

    private SimulationService simulationService;

    private static final String MINIO_ENDPOINT = "http://minio:9000";
    private static final String SIM_BUCKET = "tianjing-sim-temp";

    @BeforeEach
    void setUp() {
        simulationService = new SimulationService(taskMapper, minioClient);
        ReflectionTestUtils.setField(simulationService, "minioEndpoint", MINIO_ENDPOINT);
        ReflectionTestUtils.setField(simulationService, "simBucket", SIM_BUCKET);
    }

    // ========== uploadVideo() ==========

    @Test
    @DisplayName("uploadVideo — 上传成功返回 url 和 object_path")
    void uploadVideo_success_returnsUrlAndObjectPath() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test-video.mp4", "video/mp4", new byte[]{1, 2, 3});

        Map<String, String> result = simulationService.uploadVideo(file, "SCENE-SINTER-005");

        assertThat(result).containsKey("url").containsKey("object_path");
        assertThat(result.get("url")).startsWith(MINIO_ENDPOINT + "/" + SIM_BUCKET + "/simulation/SCENE-SINTER-005/");
        assertThat(result.get("url")).endsWith("test-video.mp4");
        assertThat(result.get("object_path")).startsWith("simulation/SCENE-SINTER-005/");
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("uploadVideo — MinIO 异常，抛出 BusinessException")
    void uploadVideo_minioThrows_throwsBusinessException() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "fail.mp4", "video/mp4", new byte[]{1});
        doThrow(new RuntimeException("MinIO 连接失败")).when(minioClient).putObject(any(PutObjectArgs.class));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> simulationService.uploadVideo(file, "SCENE-SINTER-001"))
                .satisfies(e -> assertThat(e.getDetail()).contains("视频上传失败"));
    }

    @Test
    @DisplayName("uploadVideo — 文件名为 null 时使用默认名 video.mp4")
    void uploadVideo_nullOriginalFilename_usesDefault() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", null, "video/mp4", new byte[]{1, 2});

        Map<String, String> result = simulationService.uploadVideo(file, "SCENE-STEEL-001");

        assertThat(result.get("object_path")).endsWith("video.mp4");
    }

    // ========== createTask() ==========

    @Test
    @DisplayName("createTask — 正常创建，task_id 格式 SIM-XXXXXXXX，状态为 PENDING")
    void createTask_validRequest_createsTaskWithCorrectDefaults() {
        String videoUrl = MINIO_ENDPOINT + "/" + SIM_BUCKET + "/simulation/SCENE-SINTER-005/uuid/video.mp4";
        SimulationCreateRequest req = new SimulationCreateRequest("SCENE-SINTER-005", videoUrl);
        when(taskMapper.insert(any(SimulationTask.class))).thenReturn(1);

        SimulationTask task = simulationService.createTask(req, "operator1");

        assertThat(task.getTaskId()).matches("SIM-[A-Z0-9]{8}");
        assertThat(task.getSceneId()).isEqualTo("SCENE-SINTER-005");
        assertThat(task.getStatus()).isEqualTo("PENDING");
        assertThat(task.getTaskName()).isEqualTo("video.mp4");
        assertThat(task.getVideoFileUrl()).contains("video.mp4");
        assertThat(task.getCreatedBy()).isEqualTo("operator1");
        verify(taskMapper).insert(task);
    }

    @Test
    @DisplayName("createTask — URL 中无 bucket 前缀时 objectPath 降级使用原始 URL")
    void createTask_urlWithoutBucket_usesUrlAsObjectPath() {
        String videoUrl = "http://other-host:9000/some-other-bucket/some-path/file.mp4";
        SimulationCreateRequest req = new SimulationCreateRequest("SCENE-PELLET-001", videoUrl);
        when(taskMapper.insert(any(SimulationTask.class))).thenReturn(1);

        SimulationTask task = simulationService.createTask(req, "op");

        // fallback: 整个 URL 作为 videoFileUrl
        assertThat(task.getTaskName()).isEqualTo("file.mp4");
    }

    // ========== getTaskProgress() ==========

    @Test
    @DisplayName("getTaskProgress — 任务存在且未过期，正常返回")
    void getTaskProgress_freshTask_returnsTask() {
        SimulationTask task = buildTask("RUNNING", OffsetDateTime.now().minusHours(1));
        when(taskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(task);

        SimulationTask result = simulationService.getTaskProgress("SIM-00000001");

        assertThat(result.getStatus()).isEqualTo("RUNNING");
    }

    @Test
    @DisplayName("getTaskProgress — 视频已超 72 小时，抛出 SIMULATION_VIDEO_EXPIRED")
    void getTaskProgress_expiredTask_throwsExpired() {
        SimulationTask task = buildTask("COMPLETED", OffsetDateTime.now().minusHours(73));
        when(taskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(task);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> simulationService.getTaskProgress("SIM-00000001"))
                .satisfies(e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SIMULATION_VIDEO_EXPIRED));
    }

    @Test
    @DisplayName("getTaskProgress — 恰好 72 小时，视为过期")
    void getTaskProgress_exactly72Hours_throwsExpired() {
        SimulationTask task = buildTask("PENDING", OffsetDateTime.now().minusHours(72));
        when(taskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(task);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> simulationService.getTaskProgress("SIM-00000001"))
                .satisfies(e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SIMULATION_VIDEO_EXPIRED));
    }

    @Test
    @DisplayName("getTaskProgress — uploadedAt 为 null，不执行过期检查")
    void getTaskProgress_nullUploadedAt_skipsExpiryCheck() {
        SimulationTask task = buildTask("PENDING", null);
        when(taskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(task);

        assertThatNoException().isThrownBy(() -> simulationService.getTaskProgress("SIM-00000001"));
    }

    @Test
    @DisplayName("getTaskProgress — 任务不存在，抛出 SIMULATION_TASK_NOT_FOUND")
    void getTaskProgress_notFound_throwsNotFound() {
        when(taskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> simulationService.getTaskProgress("SIM-NOTEXIST"))
                .satisfies(e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SIMULATION_TASK_NOT_FOUND));
    }

    // ========== cancelTask() ==========

    @Test
    @DisplayName("cancelTask — PENDING 状态可正常取消（状态改为 FAILED）")
    void cancelTask_pendingTask_setsStatusFailed() {
        SimulationTask task = buildTask("PENDING", OffsetDateTime.now().minusMinutes(5));
        when(taskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(task);
        when(taskMapper.updateById(any(SimulationTask.class))).thenReturn(1);

        simulationService.cancelTask("SIM-00000001", "admin");

        assertThat(task.getStatus()).isEqualTo("FAILED");
        verify(taskMapper).updateById(task);
    }

    @Test
    @DisplayName("cancelTask — RUNNING 状态可正常取消")
    void cancelTask_runningTask_setsStatusFailed() {
        SimulationTask task = buildTask("RUNNING", OffsetDateTime.now().minusMinutes(10));
        when(taskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(task);
        when(taskMapper.updateById(any(SimulationTask.class))).thenReturn(1);

        simulationService.cancelTask("SIM-00000001", "admin");

        assertThat(task.getStatus()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("cancelTask — COMPLETED 状态，抛出 RESOURCE_STATE_FORBIDDEN")
    void cancelTask_completedTask_throwsStateForbidden() {
        SimulationTask task = buildTask("COMPLETED", OffsetDateTime.now().minusHours(1));
        when(taskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(task);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> simulationService.cancelTask("SIM-00000001", "op"))
                .satisfies(e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_STATE_FORBIDDEN));

        verify(taskMapper, never()).updateById(any(SimulationTask.class));
    }

    @Test
    @DisplayName("cancelTask — FAILED 状态，抛出 RESOURCE_STATE_FORBIDDEN")
    void cancelTask_failedTask_throwsStateForbidden() {
        SimulationTask task = buildTask("FAILED", OffsetDateTime.now().minusHours(2));
        when(taskMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(task);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> simulationService.cancelTask("SIM-00000001", "op"))
                .satisfies(e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_STATE_FORBIDDEN));
    }

    // ========== 工厂方法 ==========

    private SimulationTask buildTask(String status, OffsetDateTime uploadedAt) {
        SimulationTask task = new SimulationTask();
        task.setId(1L);
        task.setTaskId("SIM-00000001");
        task.setSceneId("SCENE-SINTER-005");
        task.setTaskName("test.mp4");
        task.setVideoFileUrl("simulation/SCENE-SINTER-005/uuid/test.mp4");
        task.setStatus(status);
        task.setStartedAt(uploadedAt);
        task.setCreatedBy("tester");
        return task;
    }
}
