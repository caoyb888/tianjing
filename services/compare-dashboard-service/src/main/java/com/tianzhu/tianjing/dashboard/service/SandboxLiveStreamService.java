package com.tianzhu.tianjing.dashboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Sandbox 实时推理流服务（S3-06）
 *
 * 消费 tianjing.infer.result.sandbox，维护各场景最新 Sandbox 帧结果，
 * 结合 LiveStreamService 的生产帧，支持 SandboxCompareViewer 双路对比。
 *
 * 规范：
 *   CLAUDE.md §8.2（消费者组：compare-dashboard-live-sandbox-cg）
 *   CLAUDE.md §11.1（is_sandbox=true 帧不推送大屏生产告警）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SandboxLiveStreamService {

    private final ObjectMapper objectMapper;

    /** 各场景最新 Sandbox 推理帧（scene_id → 帧数据 map） */
    @SuppressWarnings("rawtypes")
    private final ConcurrentHashMap<String, Map> latestSandboxFrames = new ConcurrentHashMap<>();

    /** compare-live SSE 订阅者（按 scene_id 分组） */
    private final ConcurrentHashMap<String, List<SseEmitter>> sceneEmitters = new ConcurrentHashMap<>();

    /**
     * 消费 Sandbox 推理结果
     * 消费者组：compare-dashboard-live-sandbox-cg（CLAUDE.md §8.2）
     */
    @KafkaListener(
            topics = "tianjing.infer.result.sandbox",
            groupId = "compare-dashboard-live-sandbox-cg",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onSandboxResult(ConsumerRecord<String, String> record) {
        try {
                @SuppressWarnings("unchecked")
            Map<String, Object> msg = objectMapper.readValue(record.value(), Map.class);

            // SECURITY: Sandbox 结果必须标记 is_sandbox=true（CLAUDE.md §11.1）
            if (!Boolean.TRUE.equals(msg.get("is_sandbox"))) {
                log.error("SECURITY ALERT: sandbox result topic 中发现 is_sandbox=false 帧，丢弃 scene_id={}",
                        msg.get("scene_id"));
                return;
            }

            String sceneId = str(msg, "scene_id");
            latestSandboxFrames.put(sceneId, msg);

            // 广播给订阅了该场景 compare-live 的客户端
            List<SseEmitter> emitters = sceneEmitters.get(sceneId);
            if (emitters != null && !emitters.isEmpty()) {
                broadcastCompareFrame(sceneId, emitters);
            }

            log.debug("sandbox_frame_received scene={} detections={}",
                    sceneId, getSize(msg, "detections"));

        } catch (Exception e) {
            log.warn("Sandbox 推理帧解析失败 offset={} error={}", record.offset(), e.getMessage());
        }
    }

    /**
     * 注册 SSE 订阅者（by scene_id）
     */
    public SseEmitter subscribeCompare(String sceneId) {
        SseEmitter emitter = new SseEmitter(0L);
        sceneEmitters.computeIfAbsent(sceneId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeEmitter(sceneId, emitter));
        emitter.onTimeout(()    -> removeEmitter(sceneId, emitter));
        emitter.onError(e ->      removeEmitter(sceneId, emitter));
        log.info("compare-live SSE 建立 scene_id={}", sceneId);
        return emitter;
    }

    /**
     * 获取最新 Sandbox 帧（供 REST 接口直接查询）
     */
    @SuppressWarnings("rawtypes")
    public Map getLatestSandboxFrame(String sceneId) {
        return latestSandboxFrames.get(sceneId);
    }

    // ── 内部方法 ──────────────────────────────────────────────────────────────

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void broadcastCompareFrame(String sceneId, List<SseEmitter> emitters) {
        Map sandboxFrame = latestSandboxFrames.get(sceneId);
        if (sandboxFrame == null) return;

        try {
            java.util.LinkedHashMap<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("scene_id",               sceneId);
            payload.put("image_url",              str(sandboxFrame, "image_url"));
            payload.put("sandbox_detections",     sandboxFrame.getOrDefault("detections", List.of()));
            payload.put("sandbox_classifications",sandboxFrame.getOrDefault("classifications", List.of()));
            payload.put("sandbox_inference_ms",   sandboxFrame.getOrDefault("inference_time_ms", 0));
            payload.put("is_sandbox",             true);
            payload.put("timestamp_ms",           sandboxFrame.getOrDefault("timestamp_ms", 0L));
            String json = objectMapper.writeValueAsString(payload);

            emitters.removeIf(emitter -> {
                try {
                    emitter.send(SseEmitter.event().name("sandbox-frame").data(json));
                    return false;
                } catch (Exception e) {
                    log.debug("compare-live SSE 发送失败，移除客户端 scene={}", sceneId);
                    return true;
                }
            });
        } catch (Exception e) {
            log.warn("compare-live 广播失败 scene_id={} error={}", sceneId, e.getMessage());
        }
    }

    private void removeEmitter(String sceneId, SseEmitter emitter) {
        List<SseEmitter> list = sceneEmitters.get(sceneId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) sceneEmitters.remove(sceneId);
        }
    }

    @SuppressWarnings("rawtypes")
    private static String str(Map m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : "";
    }

    @SuppressWarnings("rawtypes")
    private static int getSize(Map m, String key) {
        Object v = m.get(key);
        return v instanceof List ? ((List<?>) v).size() : 0;
    }
}
