package com.tianzhu.tianjing.replay.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianzhu.tianjing.common.exception.BusinessException;
import com.tianzhu.tianjing.common.exception.ErrorCode;
import com.tianzhu.tianjing.common.response.PageResult;
import com.tianzhu.tianjing.replay.domain.SimulationTask;
import com.tianzhu.tianjing.replay.domain.SimulationVideo;
import com.tianzhu.tianjing.replay.dto.SimulationCreateRequest;
import com.tianzhu.tianjing.replay.repository.SimulationTaskMapper;
import com.tianzhu.tianjing.replay.repository.SimulationVideoMapper;
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
import java.util.List;
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
    private final SimulationVideoMapper videoMapper;
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

        // 写入初始视频记录（第一个视频）
        insertVideoRecord(task.getTaskId(), videoUrl, fileName,
                request.videoLabel() != null ? request.videoLabel() : "MIXED", 0, operator);

        // 追加视频（一次创建时上传多个）
        if (request.extraVideos() != null) {
            int order = 1;
            for (SimulationCreateRequest.ExtraVideo extra : request.extraVideos()) {
                String ep = extractObjectPath(extra.videoUrl());
                String ename = ep.substring(ep.lastIndexOf('/') + 1);
                insertVideoRecord(task.getTaskId(), extra.videoUrl(), ename,
                        extra.label() != null ? extra.label() : "MIXED", order++, operator);
            }
        }

        log.info("创建仿真任务 task_id={} scene_id={} plugin={} fps={} videos={}",
                task.getTaskId(), task.getSceneId(), pluginId, frameFps,
                1 + (request.extraVideos() != null ? request.extraVideos().size() : 0));

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
        // 填充视频列表
        task.setVideos(videoMapper.selectByTaskId(taskId));
        return task;
    }

    /**
     * 向已有任务追加视频（POST /simulations/{task_id}/videos）
     * 任务 COMPLETED / FAILED 后不允许再追加
     */
    @Transactional
    public SimulationVideo addVideo(String taskId, String videoUrl, String label, String operator) {
        SimulationTask task = getTask(taskId);
        if ("COMPLETED".equals(task.getStatus()) || "FAILED".equals(task.getStatus())) {
            throw BusinessException.of(ErrorCode.RESOURCE_STATE_FORBIDDEN, "已完成/失败的任务无法继续追加视频");
        }
        int currentCount = videoMapper.countByTaskId(taskId);
        String objectPath = extractObjectPath(videoUrl);
        String fileName = objectPath.substring(objectPath.lastIndexOf('/') + 1);
        insertVideoRecord(taskId, videoUrl, fileName,
                label != null && !label.isBlank() ? label : "MIXED", currentCount, operator);
        log.info("追加视频 task_id={} sort_order={} label={}", taskId, currentCount, label);
        // 返回刚插入的视频记录（最后一条）
        List<SimulationVideo> videos = videoMapper.selectByTaskId(taskId);
        return videos.get(videos.size() - 1);
    }

    /** 查询任务的视频列表 */
    public List<SimulationVideo> listVideos(String taskId) {
        getTask(taskId); // 验证任务存在
        return videoMapper.selectByTaskId(taskId);
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

    /** 插入 simulation_video 记录 */
    private void insertVideoRecord(String taskId, String videoUrl, String videoName,
                                   String label, int sortOrder, String operator) {
        SimulationVideo video = new SimulationVideo();
        video.setVideoId("SV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        video.setTaskId(taskId);
        video.setVideoUrl(videoUrl);
        video.setVideoName(videoName);
        video.setLabel(label);
        video.setSortOrder(sortOrder);
        video.setStatus("PENDING");
        video.setTotalFrames(0);
        video.setMatchedAlarms(0);
        video.setCreatedBy(operator);
        videoMapper.insert(video);
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
