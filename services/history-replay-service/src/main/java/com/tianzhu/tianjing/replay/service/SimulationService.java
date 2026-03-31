package com.tianzhu.tianjing.replay.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianzhu.tianjing.common.exception.BusinessException;
import com.tianzhu.tianjing.common.exception.ErrorCode;
import com.tianzhu.tianjing.common.response.PageResult;
import com.tianzhu.tianjing.replay.domain.SimulationTask;
import com.tianzhu.tianjing.replay.dto.SimulationCreateRequest;
import com.tianzhu.tianjing.replay.repository.SimulationTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * 离线仿真业务服务
 * 规范：API 接口规范 V3.1 §6.9（5 个接口）
 * MinIO 预签名 URL 生成（1h 有效）
 * 视频文件 72h 过期返回 2009
 * 仿真告警不触发外部推送（Sandbox 拦截器保证）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SimulationService {

    private final SimulationTaskMapper taskMapper;

    @Value("${tianjing.minio.endpoint:http://localhost:9000}")
    private String minioEndpoint;

    @Value("${tianjing.minio.sim-bucket:tianjing-sim-temp}")
    private String simBucket;

    public PageResult<SimulationTask> listTasks(int page, int size, String sceneId) {
        Page<SimulationTask> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<SimulationTask> wrapper = new LambdaQueryWrapper<SimulationTask>()
                .eq(SimulationTask::getIsDeleted, 0)
                .eq(sceneId != null, SimulationTask::getSceneId, sceneId)
                .orderByDesc(SimulationTask::getCreatedAt);
        var result = taskMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result.getTotal(), page, size, result.getRecords());
    }

    public SimulationTask getTask(String taskId) {
        SimulationTask task = taskMapper.selectOne(new LambdaQueryWrapper<SimulationTask>()
                .eq(SimulationTask::getTaskId, taskId)
                .eq(SimulationTask::getIsDeleted, 0));
        if (task == null) throw BusinessException.notFound(ErrorCode.SIMULATION_TASK_NOT_FOUND);
        return task;
    }

    @Transactional
    public Map<String, Object> getUploadUrl(String sceneId, String fileName, String operator) {
        // 生成 MinIO 预签名上传 URL（1h 有效）
        // 实际生成逻辑需注入 MinIO Client；此处返回构造路径
        String objectPath = "simulation/" + sceneId + "/" + UUID.randomUUID() + "/" + fileName;
        String presignedUrl = minioEndpoint + "/" + simBucket + "/" + objectPath + "?presigned=1h";

        return Map.of(
                "upload_url", presignedUrl,
                "object_path", objectPath,
                "expires_in_seconds", 3600,
                "max_size_mb", 2048
        );
    }

    @Transactional
    public SimulationTask createTask(SimulationCreateRequest request, String operator) {
        SimulationTask task = new SimulationTask();
        task.setTaskId("SIM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        task.setSceneId(request.sceneId());
        task.setVideoFileName(request.videoFileName());
        task.setVideoObjectPath(request.videoObjectPath());
        task.setStatus("PENDING");
        task.setProgressPercent(0);
        task.setUploadedAt(OffsetDateTime.now());
        task.setCreatedBy(operator);
        taskMapper.insert(task);
        log.info("创建仿真任务 task_id={} scene_id={}", task.getTaskId(), task.getSceneId());
        return task;
    }

    public SimulationTask getTaskProgress(String taskId) {
        SimulationTask task = getTask(taskId);
        // 校验 72h 有效期
        if (task.getUploadedAt() != null) {
            long hoursElapsed = ChronoUnit.HOURS.between(task.getUploadedAt(), OffsetDateTime.now());
            if (hoursElapsed >= 72) {
                throw BusinessException.of(ErrorCode.SIMULATION_VIDEO_EXPIRED,
                        "视频文件已超过 72 小时，已自动清理");
            }
        }
        return task;
    }

    @Transactional
    public void cancelTask(String taskId, String operator) {
        SimulationTask task = getTask(taskId);
        if ("COMPLETED".equals(task.getStatus()) || "FAILED".equals(task.getStatus())) {
            throw BusinessException.of(ErrorCode.RESOURCE_STATE_FORBIDDEN, "已完成/失败的仿真任务无法取消");
        }
        task.setStatus("FAILED");
        taskMapper.updateById(task);
        log.info("取消仿真任务 task_id={} operator={}", taskId, operator);
    }
}
