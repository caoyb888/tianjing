package com.tianzhu.tianjing.algomodel.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianzhu.tianjing.algomodel.domain.AlgorithmPlugin;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 算法插件响应 DTO
 * 字段名与前端 AlgorithmPlugin 类型严格对齐
 */
public record AlgorithmPluginDetail(
        String pluginId,
        @JsonProperty("name") String pluginName,
        String version,
        @JsonProperty("type") String pluginType,
        String backbone,
        String status,
        String inferBackend,
        List<String> supportedScenes,
        Map<String, Object> accuracyMetrics,
        OffsetDateTime createdAt
) {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules();

    public static AlgorithmPluginDetail from(AlgorithmPlugin p) {
        List<String> scenes = new ArrayList<>();
        Map<String, Object> accuracy = new LinkedHashMap<>();
        accuracy.put("inferenceMs", 0);

        String raw = p.getMetadataJson();
        if (raw != null && !raw.isBlank() && !raw.equals("{}")) {
            try {
                JsonNode meta = MAPPER.readTree(raw);
                // supported_scenes
                JsonNode scenesNode = meta.get("supported_scenes");
                if (scenesNode != null && scenesNode.isArray()) {
                    for (JsonNode n : scenesNode) {
                        scenes.add(n.asText());
                    }
                }
                // accuracy_metrics
                JsonNode acc = meta.get("accuracy_metrics");
                if (acc != null) {
                    JsonNode map50 = acc.get("map50");
                    if (map50 != null && !map50.isNull()) accuracy.put("map50", map50.asDouble());
                    JsonNode map5095 = acc.get("map50_95");
                    if (map5095 != null && !map5095.isNull()) accuracy.put("map50_95", map5095.asDouble());
                    JsonNode gpuMs = acc.get("inference_ms_gpu");
                    JsonNode cpuMs = acc.get("inference_ms_cpu");
                    if (gpuMs != null && !gpuMs.isNull()) {
                        accuracy.put("inferenceMs", gpuMs.asInt());
                    } else if (cpuMs != null && !cpuMs.isNull()) {
                        accuracy.put("inferenceMs", cpuMs.asInt());
                    }
                }
            } catch (Exception ignored) {
                // 解析失败返回默认值
            }
        }

        return new AlgorithmPluginDetail(
                p.getPluginId(),
                p.getPluginName(),
                p.getVersion(),
                p.getPluginType() != null ? p.getPluginType().toLowerCase() : null,
                p.getBackbone(),
                p.getStatus(),
                p.getInferBackend(),
                scenes,
                accuracy,
                p.getCreatedAt()
        );
    }
}
