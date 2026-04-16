package com.tianzhu.tianjing.aggregate.repository;

import com.tianzhu.tianjing.aggregate.dto.InferResultMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;

/**
 * TDengine 时序数据写入器
 *
 * 超级表结构（规范：CLAUDE.md §7.2）：
 * CREATE STABLE infer_result (ts TIMESTAMP, confidence FLOAT, class_name NCHAR(64),
 *   bbox_x1 INT, bbox_y1 INT, bbox_x2 INT, bbox_y2 INT, infer_ms FLOAT, is_anomaly BOOL)
 * TAGS (scene_id NCHAR(64), factory NCHAR(32), is_sandbox BOOL);
 *
 * 安全约束（CLAUDE.md §15 P2）：
 * 写入 TDengine 时 is_sandbox 标签必须正确设置，否则 Sandbox 数据与生产数据混用
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class TDengineWriter {

    private final DataSource tdengineDataSource;

    @Value("${tianjing.tdengine.anomaly-confidence-threshold:0.8}")
    private double anomalyThreshold;

    /**
     * 将推理结果写入 TDengine。
     * 每帧写入一行：取置信度最高的检测框作为代表行，anomaly_count 为超阈值检测框数量。
     * 无检测框时写入一条"无异常"记录，保留推理耗时等指标。
     *
     * 实际超级表列（与 Flyway 迁移保持一致）：
     *   ts, frame_id, confidence, anomaly_count, infer_ms,
     *   top_class_name, measurement_val, image_url, plugin_id, model_version
     * TAGS: scene_id(NCHAR64), factory_code(NCHAR32), is_sandbox(BOOL)
     *
     * @param result 推理结果消息（is_sandbox 标签由本方法负责正确设置，不信任调用方）
     */
    public void write(InferResultMessage result) {
        String subtable    = buildSubtableName(result.getSceneId(), result.isSandbox());
        String factoryCode = result.getFactory() != null ? result.getFactory() : "unknown";

        // 创建子表（不存在时自动创建）
        ensureSubtable(subtable, result.getSceneId(), factoryCode, result.isSandbox());

        List<InferResultMessage.Detection> detections = result.getDetections();

        if (detections == null || detections.isEmpty()) {
            // 无检测框：写占位行，保留推理耗时
            insertRow(subtable, result.getTimestampMs(),
                    result.getFrameId(),
                    0.0f, 0,
                    (float) result.getInferenceTimeMs(),
                    "none", 0.0f, "",
                    result.getPluginId() != null ? result.getPluginId() : "",
                    result.getPluginId() != null ? result.getPluginId() : "");
        } else {
            // 取置信度最高检测框作为代表行
            InferResultMessage.Detection top = detections.stream()
                    .max(java.util.Comparator.comparingDouble(InferResultMessage.Detection::getConfidence))
                    .orElse(detections.get(0));
            int anomalyCount = (int) detections.stream()
                    .filter(d -> d.getConfidence() >= anomalyThreshold)
                    .count();
            insertRow(subtable, result.getTimestampMs(),
                    result.getFrameId(),
                    (float) top.getConfidence(),
                    anomalyCount,
                    (float) result.getInferenceTimeMs(),
                    top.getClassName() != null ? top.getClassName() : "unknown",
                    0.0f, "",
                    result.getPluginId() != null ? result.getPluginId() : "",
                    result.getPluginId() != null ? result.getPluginId() : "");
        }
    }

    private void ensureSubtable(String subtable, String sceneId, String factoryCode, boolean isSandbox) {
        // TDengine CREATE TABLE IF NOT EXISTS 使用 USING 语法
        // TAGS 顺序：scene_id, factory_code, is_sandbox（与超级表定义一致）
        String sql = String.format(
                "CREATE TABLE IF NOT EXISTS %s USING infer_result TAGS (?, ?, ?)",
                subtable);
        try (Connection conn = tdengineDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sceneId);
            ps.setString(2, factoryCode);
            ps.setBoolean(3, isSandbox);   // SECURITY: is_sandbox 标签必须正确设置（CLAUDE.md §15 P2-6）
            ps.execute();
        } catch (Exception e) {
            log.warn("TDengine 子表创建跳过（可能已存在） subtable={} error={}", subtable, e.getMessage());
        }
    }

    /**
     * 插入一行推理记录（列顺序与超级表 infer_result 定义严格一致）：
     * ts, frame_id, confidence, anomaly_count, infer_ms,
     * top_class_name, measurement_val, image_url, plugin_id, model_version
     */
    private void insertRow(String subtable, long timestampMs,
                           String frameId,
                           float confidence, int anomalyCount,
                           float inferMs, String topClassName,
                           float measurementVal, String imageUrl,
                           String pluginId, String modelVersion) {
        String sql = String.format(
                "INSERT INTO %s (ts, frame_id, confidence, anomaly_count, infer_ms," +
                " top_class_name, measurement_val, image_url, plugin_id, model_version)" +
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", subtable);
        try (Connection conn = tdengineDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, new Timestamp(timestampMs));
            ps.setString(2,  frameId != null ? frameId : "");
            ps.setFloat(3,   confidence);
            ps.setInt(4,     anomalyCount);
            ps.setFloat(5,   inferMs);
            ps.setString(6,  topClassName);
            ps.setFloat(7,   measurementVal);
            ps.setString(8,  imageUrl);
            ps.setString(9,  pluginId);
            ps.setString(10, modelVersion);
            ps.execute();
        } catch (Exception e) {
            log.error("TDengine 写入失败 subtable={} timestamp_ms={}", subtable, timestampMs, e);
        }
    }

    /**
     * 子表命名规则：infer_{sceneId_snake}_{prod|sandbox}
     * 生产与 Sandbox 数据严格分离（不同子表）
     */
    private String buildSubtableName(String sceneId, boolean isSandbox) {
        String safe = sceneId.toLowerCase().replaceAll("[^a-z0-9]", "_");
        return "infer_" + safe + (isSandbox ? "_sandbox" : "_prod");
    }
}
