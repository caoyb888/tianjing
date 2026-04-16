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
        OffsetDateTime createdAt,
        /** 算法简要描述 */
        String description,
        /** 适合的业务维度 */
        String businessDimension,
        /**
         * 低代码编排器属性面板动态表单渲染 Schema（JSON Schema Draft-07 子集）。
         * 供前端工作流节点渲染 conf_threshold / iou_threshold 等可配置参数的滑块控件。
         * 原样透传 DB 中 ui_schema_json 列的 JSON 字符串，由前端解析。
         */
        String uiSchemaJson,
        /**
         * 推理服务端点 URL，从 metadata_json.service_endpoint 解析。
         * LOCAL_GPU 插件指向本地 GPU 推理服务（如 http://localhost:8102），
         * CLOUD_API 插件为 null（健康检查走 proxyUrl 默认配置）。
         */
        String serviceEndpoint
) {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules();

    public static AlgorithmPluginDetail from(AlgorithmPlugin p) {
        List<String> scenes = new ArrayList<>();
        Map<String, Object> accuracy = new LinkedHashMap<>();
        accuracy.put("inferenceMs", 0);

        String raw = p.getMetadataJson();
        String serviceEndpoint = null;
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
                // service_endpoint（LOCAL_GPU 插件的推理服务地址）
                JsonNode ep = meta.get("service_endpoint");
                if (ep != null && !ep.isNull() && !ep.asText().isBlank()) {
                    serviceEndpoint = ep.asText();
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
                p.getCreatedAt(),
                p.getDescription(),
                p.getBusinessDimension(),
                p.getUiSchemaJson(),
                serviceEndpoint
        );
    }
}
