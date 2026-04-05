package com.tianzhu.tianjing.replay.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianzhu.tianjing.common.exception.BusinessException;
import com.tianzhu.tianjing.common.exception.ErrorCode;
import com.tianzhu.tianjing.common.response.PageResult;
import com.tianzhu.tianjing.replay.domain.SimulationTask;
import com.tianzhu.tianjing.replay.dto.SimulationCreateRequest;
import com.tianzhu.tianjing.replay.repository.SimulationTaskMapper;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * 离线仿真业务服务
 * 规范：API 接口规范 V3.1 §6.9（5 个接口）
 * 视频文件 multipart 上传至 MinIO tianjing-sim-temp bucket
 * 视频文件 72h 过期返回 2009
 * 仿真告警不触发外部推送（Sandbox 拦截器保证）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SimulationService {

    private final SimulationTaskMapper taskMapper;
    private final MinioClient minioClient;
    private final SimulationExecutor simulationExecutor;
    private final ObjectMapper objectMapper;

    @Value("${tianjing.minio.endpoint:http://localhost:9000}")
    private String minioEndpoint;

    @Value("${tianjing.minio.sim-bucket:tianjing-sim-temp}")
    private String simBucket;

    public PageResult<SimulationTask> listTasks(int page, int size, String sceneId) {
        Page<SimulationTask> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<SimulationTask> wrapper = new LambdaQueryWrapper<SimulationTask>()
                .eq(sceneId != null, SimulationTask::getSceneId, sceneId)
                .orderByDesc(SimulationTask::getCreatedAt);
        var result = taskMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result.getTotal(), page, size, result.getRecords());
    }

    public SimulationTask getTask(String taskId) {
        SimulationTask task = taskMapper.selectOne(new LambdaQueryWrapper<SimulationTask>()
                .eq(SimulationTask::getTaskId, taskId)
                );
        if (task == null) throw BusinessException.notFound(ErrorCode.SIMULATION_TASK_NOT_FOUND);
        return task;
    }

    /**
     * 上传仿真视频到 MinIO（POST /simulations/upload-video）
     * 文件存储路径：simulation/{sceneId}/{uuid}/{filename}
     *
     * @return { url, object_path }
     */
    public Map<String, String> uploadVideo(MultipartFile file, String sceneId) {
        String rawName = file.getOriginalFilename();
        String originalName = (rawName != null && !rawName.isEmpty()) ? rawName : "video.mp4";
        String objectPath = "simulation/" + sceneId + "/" + UUID.randomUUID() + "/" + originalName;

        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(simBucket)
                    .object(objectPath)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType() != null ? file.getContentType() : "video/mp4")
                    .build());
        } catch (Exception e) {
            log.error("视频上传 MinIO 失败 object={}", objectPath, e);
            throw BusinessException.of(ErrorCode.INTERNAL_ERROR, "视频上传失败：" + e.getMessage());
        }

        String url = minioEndpoint + "/" + simBucket + "/" + objectPath;
        log.info("视频上传成功 scene_id={} url={}", sceneId, url);
        return Map.of("url", url, "object_path", objectPath);
    }

    /**
     * 创建仿真任务（POST /simulations）
     * 前端先上传视频，再携带 scene_id + video_url 调用此接口
     */
    @Transactional
    public SimulationTask createTask(SimulationCreateRequest request, String operator) {
        // 从 video_url 中提取文件名和对象路径
        String videoUrl = request.videoUrl();
        String objectPath = extractObjectPath(videoUrl);
        String fileName = objectPath.substring(objectPath.lastIndexOf('/') + 1);

        SimulationTask task = new SimulationTask();
        task.setTaskId("SIM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        task.setSceneId(request.sceneId());
        task.setTaskName(fileName);
        task.setVideoFileUrl(videoUrl);
        task.setWorkflowJson("{}");   // 仿真创建时暂无工作流配置，置空 JSON
        // 存储推理插件和抽帧配置
        String pluginId = (request.pluginId() != null && !request.pluginId().isBlank())
                ? request.pluginId() : "CLOUD-PROXY-V1";
        int frameFps = (request.frameFps() != null && request.frameFps() > 0)
                ? request.frameFps() : 1;
        try {
            task.setAlgoConfigJson(objectMapper.writeValueAsString(
                    Map.of("pluginId", pluginId, "frameFps", frameFps)));
        } catch (Exception e) {
            task.setAlgoConfigJson("{}");
        }
        task.setStatus("PENDING");
        task.setStartedAt(OffsetDateTime.now());
        task.setCreatedBy(operator);
        task.setUpdatedBy(operator);
        taskMapper.insert(task);
        log.info("创建仿真任务 task_id={} scene_id={} plugin={} fps={}", task.getTaskId(), task.getSceneId(), pluginId, frameFps);

        // 触发仿真执行引擎（异步，虚拟线程）
        simulationExecutor.executeAsync(task);

        return task;
    }

    public SimulationTask getTaskProgress(String taskId) {
        SimulationTask task = getTask(taskId);
        if (task.getStartedAt() != null) {
            long hoursElapsed = ChronoUnit.HOURS.between(task.getStartedAt(), OffsetDateTime.now());
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

    /** 从完整 URL 中提取 MinIO 对象路径（bucket 名之后的部分） */
    private String extractObjectPath(String url) {
        // url 格式: http://minio:9000/tianjing-sim-temp/simulation/...
        int bucketIdx = url.indexOf(simBucket);
        if (bucketIdx >= 0) {
            return url.substring(bucketIdx + simBucket.length() + 1);
        }
        return url; // fallback
    }
}
