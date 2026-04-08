package com.tianzhu.tianjing.replay.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianzhu.tianjing.common.exception.BusinessException;
import com.tianzhu.tianjing.common.exception.ErrorCode;
import com.tianzhu.tianjing.replay.domain.SimulationTask;
import com.tianzhu.tianjing.replay.domain.entity.SimulationFrameReviewEntity;
import com.tianzhu.tianjing.replay.dto.DatasetExportRequest;
import com.tianzhu.tianjing.replay.dto.DatasetExportStatusDTO;
import com.tianzhu.tianjing.replay.repository.SimulationFrameReviewMapper;
import com.tianzhu.tianjing.replay.repository.SimulationTaskMapper;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 仿真结果 → COCO 数据集导出服务
 * Phase 1：result_json → 过滤 → COCO JSON → zip → tianjing-datasets/{datasetVersionId}/dataset.zip
 */
@Slf4j
@Service
public class DatasetExportService {

    private final SimulationTaskMapper taskMapper;
    private final SimulationFrameReviewMapper reviewMapper;
    private final MinioClient minioClient;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate trainJdbcTemplate;

    public DatasetExportService(SimulationTaskMapper taskMapper,
                                SimulationFrameReviewMapper reviewMapper,
                                MinioClient minioClient,
                                ObjectMapper objectMapper,
                                @Qualifier("trainJdbcTemplate") JdbcTemplate trainJdbcTemplate) {
        this.taskMapper       = taskMapper;
        this.reviewMapper     = reviewMapper;
        this.minioClient      = minioClient;
        this.objectMapper     = objectMapper;
        this.trainJdbcTemplate = trainJdbcTemplate;
    }

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
        log.info("开始导出数据集 task_id={} datasetVersionId={} exportMode={}",
                taskId, req.datasetVersionId(), req.effectiveExportMode());

        // 1. 解析 result_json
        ResultJson resultJson = parseResultJson(task.getResultJson());
        if (resultJson == null || resultJson.frames() == null || resultJson.frames().isEmpty()) {
            throw new IllegalStateException("任务 result_json 为空或无帧数据，请先完成仿真推理");
        }

        List<FrameResult> frames = resultJson.frames();
        int totalFrames = frames.size();
        progressMap.put(taskId, 5);

        // 2. 加载审核记录（如果存在）
        Map<String, SimulationFrameReviewEntity> reviewMap = loadReviewMap(taskId);
        boolean hasReview = !reviewMap.isEmpty();
        if (hasReview) {
            log.info("检测到审核记录 task_id={} reviewCount={}", taskId, reviewMap.size());
        }

        // 3. 过滤：根据审核状态、置信度阈值和是否包含负样本
        double confThreshold = req.effectiveConfThreshold();
        boolean includeNegatives = req.effectiveIncludeNegatives();
        boolean reviewedOnly = req.isReviewedOnly();

        List<FrameResult> filtered = new ArrayList<>();
        for (FrameResult frame : frames) {
            SimulationFrameReviewEntity review = reviewMap.get(frame.frameId());

            // REVIEWED_ONLY 模式：只导出已审核通过的帧
            if (reviewedOnly) {
                if (review == null || !"APPROVED".equals(review.getReviewStatus())) {
                    continue; // 跳过未审核或非通过的帧
                }
            }

            // 有审核记录且状态为 REJECTED → 跳过此帧
            if (review != null && "REJECTED".equals(review.getReviewStatus())) {
                continue;
            }

            // 确定使用的检测框来源
            List<Detection> detectionsToUse;
            if (review != null && Boolean.TRUE.equals(review.getIsModified())
                    && review.getCorrectedDetectionsJson() != null) {
                // 使用人工校正结果（confidence=1.0，不再过滤）
                detectionsToUse = parseReviewDetections(review.getCorrectedDetectionsJson());
                log.debug("使用校正标注 task_id={} frame_id={} count={}",
                        taskId, frame.frameId(), detectionsToUse.size());
            } else {
                // 使用原始推理结果，按 confThreshold 过滤
                detectionsToUse = new ArrayList<>();
                if (frame.detections() != null) {
                    for (Detection d : frame.detections()) {
                        if (d.confidence() >= confThreshold) {
                            detectionsToUse.add(d);
                        }
                    }
                }
            }

            boolean hasDetection = !detectionsToUse.isEmpty();
            if (hasDetection || includeNegatives) {
                filtered.add(new FrameResult(
                        frame.frameId(),
                        frame.frameUrl(),
                        frame.timestampMs(),
                        detectionsToUse
                ));
            }
        }
        log.info("过滤后帧数 {}/{} task_id={} hasReview={}",
                filtered.size(), totalFrames, taskId, hasReview);
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

        // 8. 写入 tianjing_train.dataset / dataset_version
        int annotationCount = cocoAnnotations.size();
        int exportedFrames  = filtered.size();
        String minioPrefix  = req.datasetVersionId() + "/";
        upsertTrainDataset(task, req, exportedFrames, annotationCount, minioPrefix);

        // 9. 更新 simulation_task 状态
        ExportResult exportResult = new ExportResult(
                totalFrames, exportedFrames, annotationCount, minioPath, null);
        task.setExportStatus("EXPORTED");
        task.setExportResultJson(objectMapper.writeValueAsString(exportResult));
        taskMapper.updateById(task);
        progressMap.put(taskId, 100);

        log.info("导出完成 task_id={} frames={} annotations={} path={}",
                taskId, exportedFrames, annotationCount, minioPath);
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

    /**
     * 在 tianjing_train 库中 upsert dataset + dataset_version 记录。
     * - dataset 不存在时自动创建（source_type = LAB_REPLAY）
     * - dataset_version 不存在时 INSERT；存在时更新 sample_count
     */
    private void upsertTrainDataset(SimulationTask task, DatasetExportRequest req,
                                    int sampleCount, int annotationCount, String minioPrefix) {
        String datasetCode    = req.datasetCode() != null ? req.datasetCode() : "DS-SIM-" + task.getSceneId();
        String versionId      = req.datasetVersionId();
        String createdBy      = task.getCreatedBy() != null ? task.getCreatedBy() : "system";
        String datasetMinio   = datasetCode + "/";

        try {
            // 1. 确保 dataset 存在
            Integer exists = trainJdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM dataset WHERE dataset_code = ?", Integer.class, datasetCode);
            if (exists == null || exists == 0) {
                trainJdbcTemplate.update(
                        "INSERT INTO dataset(dataset_code, dataset_name, scene_id, factory_code, " +
                        "source_type, minio_prefix, created_by, updated_by) VALUES(?,?,?,?,'LAB_REPLAY',?,?,?)",
                        datasetCode,
                        "仿真导出数据集-" + task.getSceneId(),
                        task.getSceneId(),
                        "UNKNOWN",
                        datasetMinio,
                        createdBy, createdBy);
                log.info("自动创建 dataset dataset_code={}", datasetCode);
            }
            // 2. 更新 dataset 样本统计
            trainJdbcTemplate.update(
                    "UPDATE dataset SET total_samples = total_samples + ?, updated_by = ?, updated_at = NOW() " +
                    "WHERE dataset_code = ?",
                    sampleCount, createdBy, datasetCode);

            // 3. upsert dataset_version
            Integer vExists = trainJdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM dataset_version WHERE version_id = ?", Integer.class, versionId);
            if (vExists == null || vExists == 0) {
                trainJdbcTemplate.update(
                        "INSERT INTO dataset_version(version_id, dataset_code, version_tag, " +
                        "sample_count, minio_prefix, created_by) VALUES(?,?,?,?,?,?)",
                        versionId, datasetCode, "sim-export", sampleCount, minioPrefix, createdBy);
                log.info("创建 dataset_version version_id={} sample_count={}", versionId, sampleCount);
            } else {
                trainJdbcTemplate.update(
                        "UPDATE dataset_version SET sample_count = ? WHERE version_id = ?",
                        sampleCount, versionId);
                log.info("更新 dataset_version version_id={} sample_count={}", versionId, sampleCount);
            }
        } catch (Exception e) {
            // 写入训练库失败不影响主链路（仅记录警告）
            log.warn("写入 tianjing_train 失败，数据集版本记录未同步 versionId={} error={}",
                    versionId, e.getMessage());
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

    // -------------------------------------------------------------------------
    // 审核相关辅助方法
    // -------------------------------------------------------------------------

    /**
     * 加载该任务的审核记录 Map（key: frameId → review record）
     */
    private Map<String, SimulationFrameReviewEntity> loadReviewMap(String taskId) {
        try {
            List<SimulationFrameReviewEntity> reviews = reviewMapper.selectByTaskId(taskId);
            return reviews.stream()
                    .collect(Collectors.toMap(SimulationFrameReviewEntity::getFrameId, r -> r));
        } catch (Exception e) {
            log.warn("加载审核记录失败 task_id={} error={}", taskId, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 解析审核记录中的 corrected_detections_json
     */
    @SuppressWarnings("unchecked")
    private List<Detection> parseReviewDetections(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) {
            return Collections.emptyList();
        }
        try {
            List<Map<String, Object>> list = objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            List<Detection> result = new ArrayList<>();
            for (Map<String, Object> d : list) {
                Integer classId = (Integer) d.get("classId");
                String className = (String) d.get("className");
                double confidence = ((Number) d.get("confidence")).doubleValue();
                Map<String, Object> bbox = (Map<String, Object>) d.get("bbox");
                double x1 = ((Number) bbox.get("x1")).doubleValue();
                double y1 = ((Number) bbox.get("y1")).doubleValue();
                double x2 = ((Number) bbox.get("x2")).doubleValue();
                double y2 = ((Number) bbox.get("y2")).doubleValue();
                result.add(new Detection(classId, className, confidence, new BBox(x1, y1, x2, y2)));
            }
            return result;
        } catch (JsonProcessingException e) {
            log.warn("解析审核标注 JSON 失败: {}", e.getMessage());
            return Collections.emptyList();
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
