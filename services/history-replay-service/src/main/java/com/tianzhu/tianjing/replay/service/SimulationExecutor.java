package com.tianzhu.tianjing.replay.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianzhu.tianjing.replay.domain.SimulationTask;
import com.tianzhu.tianjing.replay.repository.SimulationTaskMapper;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 仿真执行引擎（Phase 2）
 * 流程：下载视频 → FFmpeg 抽帧 → 逐帧推理（is_sandbox=true）→ 上传帧图像 → 写 result_json
 * 在虚拟线程中异步执行，不阻塞 HTTP 请求线程
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SimulationExecutor {

    private final SimulationTaskMapper taskMapper;
    private final MinioClient minioClient;
    private final InferenceClient inferenceClient;
    private final ObjectMapper objectMapper;

    @Value("${tianjing.minio.sim-bucket:tianjing-sim-temp}")
    private String simBucket;

    @Value("${tianjing.minio.endpoint:http://localhost:9000}")
    private String minioEndpoint;

    static {
        // JavaCV 需要 AWT，在无显示环境中设置 headless
        System.setProperty("java.awt.headless", "true");
    }

    /**
     * 在虚拟线程中异步执行仿真任务
     */
    public void executeAsync(SimulationTask task) {
        Thread.ofVirtual()
                .name("sim-exec-" + task.getTaskId())
                .start(() -> {
                    try {
                        execute(task);
                    } catch (Exception e) {
                        log.error("仿真执行失败 task_id={}", task.getTaskId(), e);
                        markFailed(task, e.getMessage());
                    }
                });
    }

    // -------------------------------------------------------------------------
    // 核心执行逻辑
    // -------------------------------------------------------------------------

    private void execute(SimulationTask task) throws Exception {
        String taskId = task.getTaskId();

        // 解析 algo_config（plugin_id, frame_fps）
        AlgoConfig algoConfig = parseAlgoConfig(task.getAlgoConfigJson());
        int frameFps  = (algoConfig != null && algoConfig.frameFps()  > 0) ? algoConfig.frameFps()  : 1;
        String pluginId = (algoConfig != null && algoConfig.pluginId() != null) ? algoConfig.pluginId() : "CLOUD-PROXY-V1";

        // 1. 标记 RUNNING
        task.setStatus("RUNNING");
        task.setStartedAt(OffsetDateTime.now());
        task.setProgress(0);
        taskMapper.updateById(task);
        log.info("仿真开始 task_id={} plugin={} fps={}", taskId, pluginId, frameFps);

        // 2. 下载视频到临时文件
        String videoObjectPath = extractObjectPath(task.getVideoFileUrl());
        Path tempVideo = Files.createTempFile("sim-" + taskId + "-", ".mp4");
        try {
            try (InputStream is = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(simBucket).object(videoObjectPath).build())) {
                Files.copy(is, tempVideo, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("视频下载完成 task_id={} size={}B path={}", taskId, Files.size(tempVideo), tempVideo);

            // 3. 抽帧 + 推理
            List<FrameResult> frameResults = extractAndInfer(task, tempVideo, pluginId, frameFps);

            // 4. 写 result_json，更新状态为 COMPLETED
            int detectionFrames = (int) frameResults.stream()
                    .filter(f -> f.detections() != null && !f.detections().isEmpty())
                    .count();
            ResultJson resultJson = new ResultJson(frameResults, pluginId, frameResults.size(), detectionFrames);

            task.setStatus("COMPLETED");
            task.setFinishedAt(OffsetDateTime.now());
            task.setTotalFrames(frameResults.size());
            task.setResultJson(objectMapper.writeValueAsString(resultJson));
            task.setProgress(100);
            taskMapper.updateById(task);

            log.info("仿真完成 task_id={} 总帧数={} 检测帧={}", taskId, frameResults.size(), detectionFrames);

        } finally {
            Files.deleteIfExists(tempVideo);
        }
    }

    /**
     * 用 FFmpegFrameGrabber 按配置频率抽帧，逐帧调用推理代理，返回帧级推理结果
     */
    private List<FrameResult> extractAndInfer(SimulationTask task, Path videoPath,
                                               String pluginId, int frameFps) throws Exception {
        String taskId = task.getTaskId();
        String framesPrefix = "simulation/" + taskId + "/frames/";
        List<FrameResult> results = new ArrayList<>();

        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoPath.toFile())) {
            grabber.start();

            double videoFpsActual = grabber.getFrameRate();
            int totalFrameCount  = grabber.getLengthInFrames();

            // 每 frameStep 帧提取一帧，保证输出频率接近 frameFps
            int frameStep = (videoFpsActual > 0 && frameFps > 0)
                    ? Math.max(1, (int) Math.round(videoFpsActual / frameFps))
                    : 1;
            int estimatedOutput = Math.max(1, totalFrameCount > 0 ? totalFrameCount / frameStep : 60);

            task.setTotalFrames(estimatedOutput);
            taskMapper.updateById(task);

            log.info("视频信息 task_id={} 原始fps={} 总帧={} 每{}帧取1帧 预计输出{}帧",
                    taskId, videoFpsActual, totalFrameCount, frameStep, estimatedOutput);

            Java2DFrameConverter converter = new Java2DFrameConverter();
            int frameNum = 0;
            int extracted = 0;
            Frame frame;

            while ((frame = grabber.grabImage()) != null) {
                if (frameNum % frameStep == 0 && frame.image != null) {
                    long timestampMs = grabber.getTimestamp() / 1000;
                    String frameId = String.format("frame_%06d", extracted + 1);

                    // 转为 JPEG 字节数组
                    BufferedImage img = converter.convert(frame);
                    if (img == null) { frameNum++; continue; }

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(img, "jpg", baos);
                    byte[] jpegBytes = baos.toByteArray();

                    // 上传帧到 MinIO tianjing-sim-temp/simulation/{taskId}/frames/
                    String frameObjPath = framesPrefix + frameId + ".jpg";
                    try (InputStream fis = new ByteArrayInputStream(jpegBytes)) {
                        minioClient.putObject(PutObjectArgs.builder()
                                .bucket(simBucket)
                                .object(frameObjPath)
                                .stream(fis, jpegBytes.length, -1)
                                .contentType("image/jpeg")
                                .build());
                    }
                    String frameUrl = minioEndpoint + "/" + simBucket + "/" + frameObjPath;

                    // 调用推理代理（is_sandbox=true 由 InferenceClient 保证）
                    String imageB64 = Base64.getEncoder().encodeToString(jpegBytes);
                    List<InferenceClient.Detection> detections =
                            inferenceClient.infer(imageB64, task.getSceneId(), frameId, timestampMs);

                    // 转换为 result_json 格式
                    List<Detection> resultDetections = detections.stream()
                            .map(d -> new Detection(
                                    d.classId(), d.className(), d.confidence(),
                                    new BBox(d.bbox().x1(), d.bbox().y1(), d.bbox().x2(), d.bbox().y2())))
                            .toList();

                    results.add(new FrameResult(frameId, frameUrl, timestampMs, resultDetections));
                    extracted++;

                    // 每 10 帧更新一次进度（95% 封顶，等 COMPLETED 再写 100）
                    if (extracted % 10 == 0) {
                        int progress = Math.min(95, (int) (extracted * 95.0 / estimatedOutput));
                        task.setProgress(progress);
                        task.setTotalFrames(extracted);
                        taskMapper.updateById(task);
                    }
                }
                frameNum++;
            }
        }

        return results;
    }

    private void markFailed(SimulationTask task, String errorMsg) {
        try {
            task.setStatus("FAILED");
            task.setErrorMsg(errorMsg != null
                    ? errorMsg.substring(0, Math.min(errorMsg.length(), 500))
                    : "未知错误");
            task.setFinishedAt(OffsetDateTime.now());
            task.setProgress(0);
            taskMapper.updateById(task);
        } catch (Exception e) {
            log.error("写入 FAILED 状态失败 task_id={}", task.getTaskId(), e);
        }
    }

    private String extractObjectPath(String url) {
        int idx = url.indexOf(simBucket);
        if (idx >= 0) return url.substring(idx + simBucket.length() + 1);
        return url;
    }

    private AlgoConfig parseAlgoConfig(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) return null;
        try {
            return objectMapper.readValue(json, AlgoConfig.class);
        } catch (Exception e) {
            log.warn("解析 algo_config_json 失败: {}", e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // 内部 record 定义
    // -------------------------------------------------------------------------

    /** algo_config_json 结构（camelCase，与 SimulationService 写入保持一致） */
    record AlgoConfig(
            @JsonProperty("pluginId") String pluginId,
            @JsonProperty("frameFps") int frameFps
    ) {}

    /** result_json 结构（与 DatasetExportService 读取格式保持一致） */
    record ResultJson(List<FrameResult> frames, String pluginId,
                      Integer totalFrames, Integer detectionFrames) {}

    record FrameResult(String frameId, String frameUrl,
                       Long timestampMs, List<Detection> detections) {}

    record Detection(Integer classId, String className,
                     double confidence, BBox bbox) {}

    record BBox(double x1, double y1, double x2, double y2) {}
}
