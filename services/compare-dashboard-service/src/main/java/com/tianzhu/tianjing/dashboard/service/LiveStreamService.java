package com.tianzhu.tianjing.dashboard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianzhu.tianjing.dashboard.dto.LiveFrameEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 实时推理流服务
 * 消费 tianjing.infer.result.production，通过 SSE 广播至大屏前端。
 *
 * 规范：
 *   CLAUDE.md §8.2（消费者组命名）
 *   CLAUDE.md §11.1（is_sandbox 校验：沙箱帧不推送到大屏）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveStreamService {

    private final ObjectMapper objectMapper;

    /** SSE 订阅者列表（线程安全，支持多大屏同时接入） */
    private final List<SseEmitter> liveClients = new CopyOnWriteArrayList<>();

    /**
     * 注册新的 SSE 订阅者（由 controller 调用）
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L); // 永不超时
        liveClients.add(emitter);
        emitter.onCompletion(() -> liveClients.remove(emitter));
        emitter.onTimeout(()    -> liveClients.remove(emitter));
        emitter.onError(e ->      liveClients.remove(emitter));
        log.info("大屏实时推理 SSE 连接建立，当前连接数={}", liveClients.size());
        return emitter;
    }

    /**
     * 消费生产推理结果，广播到大屏 SSE 客户端。
     *
     * 消费者组：compare-dashboard-live-prod-cg（CLAUDE.md §8.2）
     * 注意：
     *  - is_sandbox=true 的帧不推送（CLAUDE.md §11.1 Sandbox 隔离规范）
     *  - 每帧仅推送必要字段，不包含原始图像字节
     */
    @KafkaListener(
            topics = "tianjing.infer.result.production",
            groupId = "compare-dashboard-live-prod-cg",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onInferResult(ConsumerRecord<String, String> record) {
        if (liveClients.isEmpty()) return; // 无订阅者时跳过反序列化，降低 CPU 开销

        try {
            Map<?, ?> msg = objectMapper.readValue(record.value(), Map.class);

            // CLAUDE.md §11.1：Sandbox 推理结果不推送大屏
            boolean isSandbox = Boolean.TRUE.equals(msg.get("is_sandbox"));
            if (isSandbox) return;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> detections =
                    msg.get("detections") instanceof List ? (List<Map<String, Object>>) msg.get("detections") : List.of();

            LiveFrameEventDTO event = LiveFrameEventDTO.builder()
                    .frameId(str(msg, "frame_id"))
                    .sceneId(str(msg, "scene_id"))
                    .imageUrl(str(msg, "image_url"))
                    .detections(detections)
                    .inferenceTimeMs(dbl(msg, "inference_time_ms"))
                    .dispatcherTotalMs(dbl(msg, "dispatcher_total_ms"))
                    .backend(str(msg, "backend"))
                    .timestampMs(lng(msg, "timestamp_ms"))
                    .isSandbox(false)
                    .build();

            String json = objectMapper.writeValueAsString(event);

            liveClients.removeIf(emitter -> {
                try {
                    emitter.send(SseEmitter.event().name("frame").data(json));
                    return false;
                } catch (Exception e) {
                    log.debug("SSE 发送失败，移除客户端: {}", e.getMessage());
                    return true;
                }
            });

            log.debug("实时推理帧广播 scene={} clients={} infer_ms={}",
                    event.getSceneId(), liveClients.size(), event.getInferenceTimeMs());

        } catch (Exception e) {
            log.warn("实时推理帧解析失败 offset={} error={}", record.offset(), e.getMessage());
        }
    }

    // ── 类型安全辅助方法 ─────────────────────────────────────────────────────────
    private static String str(Map<?, ?> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : "";
    }

    private static double dbl(Map<?, ?> m, String key) {
        Object v = m.get(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        return 0.0;
    }

    private static long lng(Map<?, ?> m, String key) {
        Object v = m.get(key);
        if (v instanceof Number) return ((Number) v).longValue();
        return 0L;
    }
}
