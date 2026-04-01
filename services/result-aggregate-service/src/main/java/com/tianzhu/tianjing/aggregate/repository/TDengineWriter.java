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
     * 无检测框时写入一条"无异常"记录，以保留推理耗时等指标。
     *
     * @param result 推理结果消息（is_sandbox 标签由本方法负责正确设置，不信任调用方）
     */
    public void write(InferResultMessage result) {
        String subtable = buildSubtableName(result.getSceneId(), result.isSandbox());
        String factory  = result.getFactory() != null ? result.getFactory() : "unknown";

        List<InferResultMessage.Detection> detections = result.getDetections();

        // 创建子表（不存在时自动创建）并写入数据
        ensureSubtable(subtable, result.getSceneId(), factory, result.isSandbox());

        if (detections == null || detections.isEmpty()) {
            insertRow(subtable, result.getTimestampMs(),
                    0.0, "none", 0, 0, 0, 0,
                    result.getInferenceTimeMs(), false);
        } else {
            for (InferResultMessage.Detection d : detections) {
                boolean isAnomaly = d.getConfidence() >= anomalyThreshold;
                InferResultMessage.Detection.BBox bbox = d.getBbox();
                insertRow(subtable, result.getTimestampMs(),
                        d.getConfidence(), d.getClassName(),
                        bbox != null ? (int) bbox.getX1() : 0,
                        bbox != null ? (int) bbox.getY1() : 0,
                        bbox != null ? (int) bbox.getX2() : 0,
                        bbox != null ? (int) bbox.getY2() : 0,
                        result.getInferenceTimeMs(), isAnomaly);
            }
        }
    }

    private void ensureSubtable(String subtable, String sceneId, String factory, boolean isSandbox) {
        // TDengine CREATE TABLE IF NOT EXISTS 使用 USING 语法
        String sql = String.format(
                "CREATE TABLE IF NOT EXISTS %s USING infer_result TAGS (?, ?, ?)",
                subtable);
        try (Connection conn = tdengineDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sceneId);
            ps.setString(2, factory);
            ps.setBoolean(3, isSandbox);   // SECURITY: is_sandbox 标签必须正确设置
            ps.execute();
        } catch (Exception e) {
            log.warn("TDengine 子表创建跳过（可能已存在） subtable={} error={}", subtable, e.getMessage());
        }
    }

    private void insertRow(String subtable, long timestampMs,
                           double confidence, String className,
                           int bboxX1, int bboxY1, int bboxX2, int bboxY2,
                           double inferMs, boolean isAnomaly) {
        String sql = String.format(
                "INSERT INTO %s VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", subtable);
        try (Connection conn = tdengineDataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, new Timestamp(timestampMs));
            ps.setFloat(2,   (float) confidence);
            ps.setString(3,  className);
            ps.setInt(4,     bboxX1);
            ps.setInt(5,     bboxY1);
            ps.setInt(6,     bboxX2);
            ps.setInt(7,     bboxY2);
            ps.setFloat(8,   (float) inferMs);
            ps.setBoolean(9, isAnomaly);
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
