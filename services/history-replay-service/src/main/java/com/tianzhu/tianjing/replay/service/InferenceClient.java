package com.tianzhu.tianjing.replay.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 推理代理客户端（调用 cloud-inference-proxy，端口 8092）
 * 规范：仿真推理必须使用 is_sandbox=true，不触发生产告警
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InferenceClient {

    private final ObjectMapper objectMapper;

    @Value("${tianjing.inference.proxy-url:http://localhost:8092}")
    private String proxyUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 调用推理代理对单帧进行推理
     *
     * @param imageB64    BGR JPEG Base64 编码图像
     * @param sceneId     场景 ID
     * @param frameId     帧 ID（如 frame_000001）
     * @param timestampMs 帧时间戳（毫秒）
     * @return 检测结果列表（失败时返回空列表，不抛出异常）
     */
    public List<Detection> infer(String imageB64, String sceneId, String frameId, long timestampMs) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("image_b64", imageB64);
        body.put("scene_id", sceneId);
        body.put("frame_id", frameId);
        body.put("timestamp_ms", timestampMs);
        body.put("is_sandbox", true);  // 仿真推理必须标记 sandbox，规范 §11
        body.put("conf_threshold", 0.5);

        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(proxyUrl + "/infer"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                InferResponse resp = objectMapper.readValue(response.body(), InferResponse.class);
                return resp.detections() != null ? resp.detections() : List.of();
            } else {
                log.warn("推理请求非 200 frame_id={} status={}", frameId, response.statusCode());
                return List.of();
            }
        } catch (Exception e) {
            log.warn("推理请求异常 frame_id={}: {}", frameId, e.getMessage());
            return List.of();
        }
    }

    // -------------------------------------------------------------------------
    // 推理代理响应 Schema（与 cloud-inference-proxy/src/main.py 对齐）
    // Python FastAPI 使用 snake_case，需 @JsonProperty 映射
    // -------------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InferResponse(
            @JsonProperty("plugin_id")          String pluginId,
            @JsonProperty("detections")         List<Detection> detections,
            @JsonProperty("inference_time_ms")  Double inferenceTimeMs,
            @JsonProperty("timestamp_ms")       Long timestampMs
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Detection(
            @JsonProperty("class_id")   Integer classId,
            @JsonProperty("class_name") String className,
            @JsonProperty("confidence") double confidence,
            @JsonProperty("bbox")       BBox bbox
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BBox(
            @JsonProperty("x1") double x1,
            @JsonProperty("y1") double y1,
            @JsonProperty("x2") double x2,
            @JsonProperty("y2") double y2
    ) {}
}
