package com.tianzhu.tianjing.replay.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianzhu.tianjing.replay.domain.SimulationTask;
import com.tianzhu.tianjing.replay.domain.SimulationVideo;
import com.tianzhu.tianjing.replay.repository.SimulationTaskMapper;
import com.tianzhu.tianjing.replay.repository.SimulationVideoMapper;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
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

import static org.bytedeco.ffmpeg.global.avcodec.AV_PKT_DATA_DISPLAYMATRIX;
import static org.bytedeco.ffmpeg.global.avutil.av_display_rotation_get;
import static org.bytedeco.ffmpeg.global.avformat.av_stream_get_side_data;

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
    private final SimulationVideoMapper videoMapper;
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

        // 1. 标记 RUNNING，并检查推理模型是否就绪（未加载则自动预热）
        task.setStatus("RUNNING");
        task.setStartedAt(OffsetDateTime.now());
        task.setProgress(0);
        taskMapper.updateById(task);
        log.info("仿真开始 task_id={} plugin={} fps={}", taskId, pluginId, frameFps);

        InferenceClient.ModelHealth health = inferenceClient.getHealth();
        if (!health.onnxModelLoaded()) {
            log.info("推理模型未就绪，开始自动预热 task_id={}", taskId);
            health = inferenceClient.warmup();
            log.info("推理模型预热完成 loaded={} task_id={}", health.onnxModelLoaded(), taskId);
        }

        // 2. 从 simulation_video 表获取本任务全部视频（按 sort_order 顺序）
        //    若表中无记录（旧任务兼容），回退到 task.videoFileUrl
        List<SimulationVideo> videos = videoMapper.selectByTaskId(taskId);
        if (videos.isEmpty()) {
            // 兼容旧数据：直接用 task.video_file_url 构造虚拟记录
            SimulationVideo fallback = new SimulationVideo();
            fallback.setVideoUrl(task.getVideoFileUrl());
            fallback.setVideoName("video.mp4");
            fallback.setSortOrder(0);
            videos = List.of(fallback);
        }
        log.info("本任务共 {} 个视频 task_id={}", videos.size(), taskId);

        // 3. 逐个视频下载 → 抽帧 → 推理，汇总所有帧结果
        List<FrameResult> allFrameResults = new ArrayList<>();

        for (SimulationVideo video : videos) {
            String videoUrl = video.getVideoUrl();
            String videoName = video.getVideoName() != null ? video.getVideoName() : "video.mp4";
            String videoObjectPath = extractObjectPath(videoUrl);
            Path tempVideo = Files.createTempFile("sim-" + taskId + "-v" + video.getSortOrder() + "-", ".mp4");

            log.info("开始处理视频 [{}/{}] {} task_id={}",
                    video.getSortOrder() + 1, videos.size(), videoName, taskId);

            // 将当前视频标记为 RUNNING
            if (video.getId() != null) {
                video.setStatus("RUNNING");
                videoMapper.updateById(video);
            }

            try {
                try (InputStream is = minioClient.getObject(GetObjectArgs.builder()
                        .bucket(simBucket).object(videoObjectPath).build())) {
                    Files.copy(is, tempVideo, StandardCopyOption.REPLACE_EXISTING);
                }
                log.info("视频下载完成 {} size={}B", videoName, Files.size(tempVideo));

                // 抽帧 + 推理（帧ID加视频序号前缀，避免多视频帧ID冲突）
                List<FrameResult> videoFrames = extractAndInfer(task, tempVideo, pluginId, frameFps,
                        video.getSortOrder(), allFrameResults.size());

                int videoDetections = (int) videoFrames.stream()
                        .filter(f -> f.detections() != null && !f.detections().isEmpty())
                        .count();

                allFrameResults.addAll(videoFrames);

                // 更新当前视频的统计字段
                if (video.getId() != null) {
                    video.setTotalFrames(videoFrames.size());
                    video.setMatchedAlarms(videoDetections);
                    video.setStatus("COMPLETED");
                    videoMapper.updateById(video);
                }
                log.info("视频 {} 推理完成 帧数={} 检测帧={}", videoName, videoFrames.size(), videoDetections);

            } catch (Exception e) {
                log.error("视频 {} 推理失败 task_id={}", videoName, taskId, e);
                if (video.getId() != null) {
                    video.setStatus("FAILED");
                    video.setErrorMsg(e.getMessage() != null
                            ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 500))
                            : "推理失败");
                    videoMapper.updateById(video);
                }
                // 单个视频失败不中断整个任务，继续处理下一个
            } finally {
                Files.deleteIfExists(tempVideo);
            }

            // 更新任务整体进度（按视频数量均分）
            int overallProgress = Math.min(95,
                    (video.getSortOrder() + 1) * 95 / videos.size());
            task.setProgress(overallProgress);
            task.setTotalFrames(allFrameResults.size());
            taskMapper.updateById(task);
        }

        // 4. 汇总写入 result_json，更新任务为 COMPLETED
        int totalDetectionFrames = (int) allFrameResults.stream()
                .filter(f -> f.detections() != null && !f.detections().isEmpty())
                .count();
        ResultJson resultJson = new ResultJson(allFrameResults, pluginId,
                allFrameResults.size(), totalDetectionFrames);

        task.setStatus("COMPLETED");
        task.setFinishedAt(OffsetDateTime.now());
        task.setTotalFrames(allFrameResults.size());
        task.setMatchedAlarms(totalDetectionFrames);
        task.setResultJson(objectMapper.writeValueAsString(resultJson));
        task.setProgress(100);
        taskMapper.updateById(task);

        log.info("仿真全部完成 task_id={} 视频数={} 总帧数={} 检测帧={}",
                taskId, videos.size(), allFrameResults.size(), totalDetectionFrames);
    }

    /**
     * 用 FFmpegFrameGrabber 按配置频率抽帧，逐帧调用推理代理，返回本视频的帧级推理结果
     *
     * @param videoIndex    当前视频在任务中的序号（0-based），用于生成不重复的帧ID前缀
     * @param globalOffset  已处理的全局帧数偏移，用于生成连续的全局帧序号
     */
    private List<FrameResult> extractAndInfer(SimulationTask task, Path videoPath,
                                               String pluginId, int frameFps,
                                               int videoIndex, int globalOffset) throws Exception {
        String taskId = task.getTaskId();
        String framesPrefix = "simulation/" + taskId + "/frames/";
        List<FrameResult> results = new ArrayList<>();

        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoPath.toFile())) {
            grabber.start();

            // 读取视频旋转角度（手机竖拍视频常见 90/270°）
            // 优先从 AVStream display matrix side data 读取（tkhd 矩阵），
            // 回退到 stream metadata "rotate" tag
            // FFmpegFrameGrabber 不自动应用旋转，需手动修正
            int rotationDeg = readVideoRotation(grabber);
            if (rotationDeg != 0) {
                log.info("视频[{}] 检测到旋转 {}°，将自动修正帧方向", videoIndex, rotationDeg);
            }

            double videoFpsActual = grabber.getFrameRate();
            int totalFrameCount  = grabber.getLengthInFrames();

            int frameStep = (videoFpsActual > 0 && frameFps > 0)
                    ? Math.max(1, (int) Math.round(videoFpsActual / frameFps))
                    : 1;
            int estimatedOutput = Math.max(1, totalFrameCount > 0 ? totalFrameCount / frameStep : 60);

            log.info("视频[{}] 原始fps={} 总帧={} 每{}帧取1帧 预计输出{}帧",
                    videoIndex, videoFpsActual, totalFrameCount, frameStep, estimatedOutput);

            Java2DFrameConverter converter = new Java2DFrameConverter();
            int frameNum = 0;
            int extracted = 0;
            Frame frame;

            while ((frame = grabber.grabImage()) != null) {
                if (frameNum % frameStep == 0 && frame.image != null) {
                    long timestampMs = grabber.getTimestamp() / 1000;
                    // 全局唯一帧ID：v{视频序号}_frame_{全局序号}
                    String frameId = String.format("v%d_frame_%06d", videoIndex, globalOffset + extracted + 1);

                    BufferedImage img = converter.convert(frame);
                    if (img == null) { frameNum++; continue; }

                    // 应用旋转修正（rotation 元数据记录的是拍摄时顺时针角度）
                    if (rotationDeg != 0) {
                        img = rotateImage(img, rotationDeg);
                    }

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(img, "jpg", baos);
                    byte[] jpegBytes = baos.toByteArray();

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

                    String imageB64 = Base64.getEncoder().encodeToString(jpegBytes);
                    List<InferenceClient.Detection> detections =
                            inferenceClient.infer(imageB64, task.getSceneId(), frameId, timestampMs);

                    List<Detection> resultDetections = detections.stream()
                            .map(d -> new Detection(
                                    d.classId(), d.className(), d.confidence(),
                                    new BBox(d.bbox().x1(), d.bbox().y1(), d.bbox().x2(), d.bbox().y2())))
                            .toList();

                    results.add(new FrameResult(frameId, frameUrl, timestampMs, resultDetections));
                    extracted++;
                }
                frameNum++;
            }
        }

        return results;
    }

    /**
     * 读取视频流的显示旋转角度（度，顺时针为正）
     * 优先读 AVStream display matrix side data（tkhd 矩阵），
     * 回退到 stream metadata "rotate" tag
     */
    private static int readVideoRotation(FFmpegFrameGrabber grabber) {
        try {
            AVFormatContext fmtCtx = grabber.getFormatContext();
            if (fmtCtx == null) return 0;
            int vsIdx = grabber.getVideoStream();
            if (vsIdx < 0 || vsIdx >= fmtCtx.nb_streams()) return 0;
            AVStream stream = fmtCtx.streams(vsIdx);

            // 方法一：AVStream display matrix side data（tkhd 变换矩阵）
            // av_stream_get_side_data 返回 BytePointer，指向 36 字节的 3x3 int32 矩阵
            BytePointer sdPtr = av_stream_get_side_data(stream, AV_PKT_DATA_DISPLAYMATRIX, (org.bytedeco.javacpp.SizeTPointer) null);
            if (sdPtr != null && !sdPtr.isNull()) {
                try (IntPointer matrixPtr = new IntPointer(sdPtr).capacity(9)) {
                    // av_display_rotation_get 返回逆时针角度（正值），取反得到顺时针
                    double rotCcw = av_display_rotation_get(matrixPtr);
                    if (!Double.isNaN(rotCcw)) {
                        int rotCw = (int) Math.round(-rotCcw);
                        return ((rotCw % 360) + 360) % 360;
                    }
                }
            }

            // 方法二：stream metadata "rotate" tag（部分安卓设备写入）
            String rotateMeta = grabber.getVideoMetadata("rotate");
            if (rotateMeta != null && !rotateMeta.isBlank()) {
                try { return ((Integer.parseInt(rotateMeta.trim()) % 360) + 360) % 360; }
                catch (NumberFormatException ignored) {}
            }
        } catch (Exception e) {
            // 读取失败不影响主流程，直接返回 0（不旋转）
        }
        return 0;
    }

    /**
     * 按视频 rotation 元数据旋转图像（顺时针角度）
     * 支持 90 / 180 / 270 度，其余角度原样返回
     */
    private static BufferedImage rotateImage(BufferedImage src, int degrees) {
        int normalizedDeg = ((degrees % 360) + 360) % 360;
        if (normalizedDeg == 0) return src;

        int w = src.getWidth();
        int h = src.getHeight();

        int newW = (normalizedDeg == 90 || normalizedDeg == 270) ? h : w;
        int newH = (normalizedDeg == 90 || normalizedDeg == 270) ? w : h;

        BufferedImage dst = new BufferedImage(newW, newH, src.getType() != 0 ? src.getType() : BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = dst.createGraphics();

        AffineTransform at = new AffineTransform();
        switch (normalizedDeg) {
            case 90  -> { at.translate(newW, 0);    at.rotate(Math.PI / 2);  }
            case 180 -> { at.translate(newW, newH); at.rotate(Math.PI);      }
            case 270 -> { at.translate(0, newH);    at.rotate(-Math.PI / 2); }
        }
        g2d.setTransform(at);
        g2d.drawImage(src, 0, 0, null);
        g2d.dispose();
        return dst;
    }

    private void markFailed(SimulationTask task, String errorMsg) {
        try {
            String truncatedMsg = errorMsg != null
                    ? errorMsg.substring(0, Math.min(errorMsg.length(), 500))
                    : "未知错误";
            task.setStatus("FAILED");
            task.setErrorMsg(truncatedMsg);
            task.setFinishedAt(OffsetDateTime.now());
            task.setProgress(0);
            taskMapper.updateById(task);
            // 将所有未完成的视频记录标记为 FAILED
            try {
                videoMapper.selectByTaskId(task.getTaskId()).stream()
                        .filter(v -> !"COMPLETED".equals(v.getStatus()))
                        .forEach(v -> {
                            v.setStatus("FAILED");
                            v.setErrorMsg(truncatedMsg);
                            videoMapper.updateById(v);
                        });
            } catch (Exception ex) {
                log.warn("更新视频 FAILED 状态失败: {}", ex.getMessage());
            }
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
