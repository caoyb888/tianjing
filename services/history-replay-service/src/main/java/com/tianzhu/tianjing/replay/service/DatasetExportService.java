package com.tianzhu.tianjing.replay.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianzhu.tianjing.common.exception.BusinessException;
import com.tianzhu.tianjing.common.exception.ErrorCode;
import com.tianzhu.tianjing.replay.domain.SimulationTask;
import com.tianzhu.tianjing.replay.dto.DatasetExportRequest;
import com.tianzhu.tianjing.replay.dto.DatasetExportStatusDTO;
import com.tianzhu.tianjing.replay.repository.SimulationTaskMapper;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 仿真结果 → COCO 数据集导出服务
 * Phase 1：result_json → 过滤 → COCO JSON → zip → tianjing-datasets/{datasetVersionId}/dataset.zip
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasetExportService {

    private final SimulationTaskMapper taskMapper;
    private final MinioClient minioClient;
    private final ObjectMapper objectMapper;

    @Value("${tianjing.minio.sim-bucket:tianjing-sim-temp}")
    private String simBucket;

    @Value("${tianjing.minio.datasets-bucket:tianjing-datasets}")
    private String datasetsBucket;

    @Value("${tianjing.minio.endpoint:http://localhost:9000}")
    private String minioEndpoint;

    /** 内存进度表：taskId → 百分比（异步更新） */
    private final Map<String, Integer> progressMap = new ConcurrentHashMap<>();

    /**
     * 异步触发导出（在虚拟线程中执行）
     * 调用方立即返回，通过 getExportStatus 轮询进度
     */
    public void startExportAsync(SimulationTask task, DatasetExportRequest req) {
        // 幂等检查：已在导出中则跳过
        if ("EXPORTING".equals(task.getExportStatus())) {
            log.warn("任务 {} 已在导出中，跳过重复请求", task.getTaskId());
            return;
        }
        // 更新状态为 EXPORTING
        task.setExportStatus("EXPORTING");
        task.setDatasetVersionId(req.datasetVersionId());
        taskMapper.updateById(task);
        progressMap.put(task.getTaskId(), 0);

        Thread.ofVirtual().name("export-" + task.getTaskId()).start(() -> {
            try {
                doExport(task, req);
            } catch (Exception e) {
                log.error("导出失败 task_id={}", task.getTaskId(), e);
                markFailed(task, e.getMessage());
            }
        });
    }

    /**
     * 查询导出状态
     */
    public DatasetExportStatusDTO getExportStatus(SimulationTask task) {
        String status = task.getExportStatus();
        if (status == null) {
            return new DatasetExportStatusDTO("PENDING", 0, 0, 0, 0, null, null, null);
        }

        // 解析 export_result_json
        ExportResult result = parseExportResult(task.getExportResultJson());
        int progress = switch (status) {
            case "EXPORTING" -> progressMap.getOrDefault(task.getTaskId(), 0);
            case "EXPORTED"  -> 100;
            default          -> 0;
        };

        return new DatasetExportStatusDTO(
                status,
                progress,
                result != null ? result.totalFrames() : 0,
                result != null ? result.exportedFrames() : 0,
                result != null ? result.annotationCount() : 0,
                task.getDatasetVersionId(),
                result != null ? result.minioPath() : null,
                result != null ? result.errorMsg() : null
        );
    }

    // -------------------------------------------------------------------------
    // 核心导出逻辑
    // -------------------------------------------------------------------------

    private void doExport(SimulationTask task, DatasetExportRequest req) throws Exception {
        String taskId = task.getTaskId();
        log.info("开始导出数据集 task_id={} datasetVersionId={}", taskId, req.datasetVersionId());

        // 1. 解析 result_json
        ResultJson resultJson = parseResultJson(task.getResultJson());
        if (resultJson == null || resultJson.frames() == null || resultJson.frames().isEmpty()) {
            throw new IllegalStateException("任务 result_json 为空或无帧数据，请先完成仿真推理");
        }

        List<FrameResult> frames = resultJson.frames();
        int totalFrames = frames.size();
        progressMap.put(taskId, 5);

        // 2. 过滤：根据置信度阈值和是否包含负样本
        double confThreshold = req.effectiveConfThreshold();
        boolean includeNegatives = req.effectiveIncludeNegatives();

        List<FrameResult> filtered = new ArrayList<>();
        for (FrameResult frame : frames) {
            List<Detection> passedDetections = new ArrayList<>();
            if (frame.detections() != null) {
                for (Detection d : frame.detections()) {
                    if (d.confidence() >= confThreshold) {
                        passedDetections.add(d);
                    }
                }
            }
            boolean hasDetection = !passedDetections.isEmpty();
            if (hasDetection || includeNegatives) {
                filtered.add(new FrameResult(
                        frame.frameId(),
                        frame.frameUrl(),
                        frame.timestampMs(),
                        passedDetections
                ));
            }
        }
        log.info("过滤后帧数 {}/{} task_id={}", filtered.size(), totalFrames, taskId);
        progressMap.put(taskId, 10);

        // 3. 构建 COCO 结构
        // 收集所有类别
        Map<String, Integer> categoryMap = new LinkedHashMap<>();
        int catIdCounter = 1;
        for (FrameResult frame : filtered) {
            for (Detection d : frame.detections()) {
                if (!categoryMap.containsKey(d.className())) {
                    categoryMap.put(d.className(), catIdCounter++);
                }
            }
        }
        // 确保至少有一个默认类别
        if (categoryMap.isEmpty()) {
            categoryMap.put("object", 1);
        }

        List<Map<String, Object>> cocoImages = new ArrayList<>();
        List<Map<String, Object>> cocoAnnotations = new ArrayList<>();
        List<Map<String, Object>> cocoCategories = new ArrayList<>();

        categoryMap.forEach((name, cid) -> {
            Map<String, Object> cat = new LinkedHashMap<>();
            cat.put("id", cid);
            cat.put("name", name);
            cocoCategories.add(cat);
        });

        int annotationId = 1;
        for (int i = 0; i < filtered.size(); i++) {
            FrameResult frame = filtered.get(i);
            int imageId = i + 1;
            String fileName = extractFileName(frame.frameUrl());

            Map<String, Object> img = new LinkedHashMap<>();
            img.put("id", imageId);
            img.put("file_name", fileName);
            img.put("width", 1920);
            img.put("height", 1080);
            cocoImages.add(img);

            for (Detection d : frame.detections()) {
                // COCO bbox: [x, y, width, height]（左上角坐标 + 宽高）
                double x = d.bbox().x1();
                double y = d.bbox().y1();
                double w = d.bbox().x2() - d.bbox().x1();
                double h = d.bbox().y2() - d.bbox().y1();
                double area = w * h;

                Map<String, Object> ann = new LinkedHashMap<>();
                ann.put("id", annotationId++);
                ann.put("image_id", imageId);
                ann.put("category_id", categoryMap.getOrDefault(d.className(), 1));
                ann.put("bbox", List.of(x, y, w, h));
                ann.put("area", area);
                ann.put("iscrowd", 0);
                cocoAnnotations.add(ann);
            }

            if ((i + 1) % 10 == 0) {
                int pct = 10 + (int) ((i + 1) * 60.0 / filtered.size());
                progressMap.put(taskId, Math.min(pct, 70));
            }
        }

        Map<String, Object> coco = new LinkedHashMap<>();
        coco.put("images", cocoImages);
        coco.put("annotations", cocoAnnotations);
        coco.put("categories", cocoCategories);
        String annotationsJson = objectMapper.writeValueAsString(coco);
        progressMap.put(taskId, 72);

        // 4. 创建临时目录，下载帧图像 + 写 annotations.json + 打包 zip
        Path tmpDir = Files.createTempDirectory("sim-export-" + taskId + "-");
        Path imagesDir = tmpDir.resolve("images");
        Files.createDirectories(imagesDir);
        Path annotationsFile = tmpDir.resolve("annotations.json");
        Files.writeString(annotationsFile, annotationsJson);

        // 并发下载帧图像（虚拟线程，最多 4 并发）
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var semaphore = new java.util.concurrent.Semaphore(4);
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();

            for (FrameResult frame : filtered) {
                String frameUrl = frame.frameUrl();
                String fileName = extractFileName(frameUrl);
                Path destPath = imagesDir.resolve(fileName);

                futures.add(executor.submit(() -> {
                    semaphore.acquire();
                    try {
                        downloadFrameToFile(frameUrl, destPath);
                    } finally {
                        semaphore.release();
                    }
                    return null;
                }));
            }
            for (var f : futures) {
                f.get(); // 等待所有下载完成
            }
        }
        progressMap.put(taskId, 85);

        // 5. 打包 zip
        Path zipPath = tmpDir.resolve("dataset.zip");
        packZip(tmpDir, imagesDir, annotationsFile, zipPath);
        progressMap.put(taskId, 92);

        // 6. 上传到 tianjing-datasets/{datasetVersionId}/dataset.zip
        String minioKey = req.datasetVersionId() + "/dataset.zip";
        long zipSize = Files.size(zipPath);
        try (InputStream is = Files.newInputStream(zipPath)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(datasetsBucket)
                    .object(minioKey)
                    .stream(is, zipSize, -1)
                    .contentType("application/zip")
                    .build());
        }
        String minioPath = datasetsBucket + "/" + minioKey;
        log.info("数据集 zip 上传成功 path={} size={}B", minioPath, zipSize);
        progressMap.put(taskId, 98);

        // 7. 清理临时文件
        deleteRecursively(tmpDir);

        // 8. 更新 simulation_task 状态
        int annotationCount = cocoAnnotations.size();
        ExportResult exportResult = new ExportResult(
                totalFrames, filtered.size(), annotationCount, minioPath, null);
        task.setExportStatus("EXPORTED");
        task.setExportResultJson(objectMapper.writeValueAsString(exportResult));
        taskMapper.updateById(task);
        progressMap.put(taskId, 100);

        log.info("导出完成 task_id={} frames={} annotations={} path={}",
                taskId, filtered.size(), annotationCount, minioPath);
    }

    private void downloadFrameToFile(String frameUrl, Path destPath) throws Exception {
        // frameUrl 格式：http://localhost:9000/tianjing-sim-temp/simulation/{taskId}/frames/xxx.jpg
        // 提取 object key（bucket 名之后的部分）
        String objectKey = extractSimObjectKey(frameUrl);
        try (InputStream is = minioClient.getObject(GetObjectArgs.builder()
                .bucket(simBucket)
                .object(objectKey)
                .build())) {
            Files.copy(is, destPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String extractSimObjectKey(String url) {
        // 支持 minio:// 和 http:// 两种格式
        int idx = url.indexOf(simBucket);
        if (idx >= 0) {
            return url.substring(idx + simBucket.length() + 1);
        }
        // fallback：截取 bucket 后的路径
        idx = url.indexOf("/simulation/");
        if (idx >= 0) {
            return url.substring(idx + 1);
        }
        return url;
    }

    private void packZip(Path tmpDir, Path imagesDir, Path annotationsFile, Path zipPath) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            // 添加 images/ 目录下的所有文件
            try (var stream = Files.list(imagesDir)) {
                for (Path img : stream.sorted().toList()) {
                    zos.putNextEntry(new ZipEntry("images/" + img.getFileName()));
                    Files.copy(img, zos);
                    zos.closeEntry();
                }
            }
            // 添加 annotations.json
            zos.putNextEntry(new ZipEntry("annotations.json"));
            Files.copy(annotationsFile, zos);
            zos.closeEntry();
        }
    }

    private void markFailed(SimulationTask task, String errorMsg) {
        try {
            ExportResult result = new ExportResult(0, 0, 0, null, errorMsg);
            task.setExportStatus("EXPORT_FAILED");
            task.setExportResultJson(objectMapper.writeValueAsString(result));
            taskMapper.updateById(task);
        } catch (Exception ex) {
            log.error("写入 EXPORT_FAILED 状态失败 task_id={}", task.getTaskId(), ex);
        }
        progressMap.remove(task.getTaskId());
    }

    private void deleteRecursively(Path dir) {
        try {
            if (Files.exists(dir)) {
                Files.walk(dir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try { Files.delete(p); } catch (IOException ignore) {}
                        });
            }
        } catch (IOException ignore) {}
    }

    private static String extractFileName(String url) {
        if (url == null) return "frame.jpg";
        int idx = url.lastIndexOf('/');
        return idx >= 0 ? url.substring(idx + 1) : url;
    }

    // -------------------------------------------------------------------------
    // JSON 解析模型（内部 record）
    // -------------------------------------------------------------------------

    private ResultJson parseResultJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, ResultJson.class);
        } catch (JsonProcessingException e) {
            log.error("解析 result_json 失败: {}", e.getMessage());
            return null;
        }
    }

    private ExportResult parseExportResult(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, ExportResult.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    // result_json 结构
    record ResultJson(List<FrameResult> frames, String pluginId,
                      Integer totalFrames, Integer detectionFrames) {}

    record FrameResult(String frameId, String frameUrl,
                       Long timestampMs, List<Detection> detections) {}

    record Detection(Integer classId, String className,
                     double confidence, BBox bbox) {}

    record BBox(double x1, double y1, double x2, double y2) {}

    // export_result_json 结构
    record ExportResult(int totalFrames, int exportedFrames,
                        int annotationCount, String minioPath, String errorMsg) {}
}
