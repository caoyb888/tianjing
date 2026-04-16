package com.tianzhu.tianjing.algomodel.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianzhu.tianjing.algomodel.dto.PluginHealthResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 算法插件可用性检测器
 * 通过向推理代理发送测试图像并验证响应，判断算法是否可正常使用
 */
@Slf4j
@Component
public class PluginHealthChecker {

    private final ObjectMapper objectMapper;

    @Value("${tianjing.inference.proxy-url:http://localhost:8092}")
    private String proxyUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public PluginHealthChecker(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 对指定插件执行可用性探测：
     * 1. 生成 64×64 灰阶测试图（无需外部文件）
     * 2. 发送至推理代理 POST /infer（is_sandbox=true）
     * 3. 解析响应，返回可用性结果
     */
    public PluginHealthResult check(String pluginId, String sceneId) {
        long startMs = System.currentTimeMillis();
        try {
            // 1. 生成测试图（64×64 暗灰色，模拟工业摄像头低曝光帧）
            String imageB64 = generateTestImageB64();

            // 2. 构造推理请求
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("image_b64", imageB64);
            body.put("scene_id",  sceneId != null ? sceneId : "HEALTH-CHECK");
            body.put("frame_id",  "health-check-frame");
            body.put("timestamp_ms", System.currentTimeMillis());
            body.put("is_sandbox", true);     // 健康检测永远走 sandbox，不触发告警
            body.put("conf_threshold", 0.5);

            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(proxyUrl + "/infer"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            // 3. 发送并计时
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long responseMs = System.currentTimeMillis() - startMs;

            if (response.statusCode() == 200) {
                InferResponse infer = objectMapper.readValue(response.body(), InferResponse.class);
                int detectionCount = infer.detections() != null ? infer.detections().size() : 0;
                String msg = String.format("推理代理响应正常，返回 %d 个检测框（推理耗时 %.1f ms）",
                        detectionCount,
                        infer.inferenceTimeMs() != null ? infer.inferenceTimeMs() : 0.0);
                log.info("算法健康检测通过 plugin_id={} responseMs={}", pluginId, responseMs);
                return PluginHealthResult.ok(responseMs, msg,
                        infer.backend() != null ? infer.backend() : "unknown");
            } else {
                String msg = "推理代理返回非 200 状态：HTTP " + response.statusCode();
                log.warn("算法健康检测失败 plugin_id={} status={}", pluginId, response.statusCode());
                return PluginHealthResult.fail(responseMs, msg, null);
            }

        } catch (java.net.ConnectException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            String msg = "无法连接推理代理（" + proxyUrl + "），请确认服务已启动";
            log.warn("算法健康检测失败（连接拒绝）plugin_id={}", pluginId);
            return PluginHealthResult.fail(elapsed, msg, null);
        } catch (java.net.http.HttpTimeoutException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            String msg = "推理代理响应超时（>15s），请检查推理服务负载";
            log.warn("算法健康检测超时 plugin_id={}", pluginId);
            return PluginHealthResult.fail(elapsed, msg, null);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startMs;
            String msg = "检测异常：" + e.getMessage();
            log.error("算法健康检测异常 plugin_id={}", pluginId, e);
            return PluginHealthResult.fail(elapsed, msg, null);
        }
    }

    /** 生成 64×64 暗灰色测试图像，Base64 编码为 JPEG */
    private String generateTestImageB64() throws Exception {
        System.setProperty("java.awt.headless", "true");
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(60, 60, 60));   // 暗灰，模拟工业相机典型低亮度场景
        g.fillRect(0, 0, 64, 64);
        g.setColor(new Color(120, 120, 120));
        g.fillRect(10, 10, 20, 20);          // 加一个矩形块，让检测算法有内容可处理
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", baos);
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    // ── 推理代理响应 Schema ──────────────────────────────────────────────────────
    @JsonIgnoreProperties(ignoreUnknown = true)
    record InferResponse(
            @JsonProperty("detections")        List<Object> detections,
            @JsonProperty("inference_time_ms") Double inferenceTimeMs,
            @JsonProperty("backend")           String backend
    ) {}
}
