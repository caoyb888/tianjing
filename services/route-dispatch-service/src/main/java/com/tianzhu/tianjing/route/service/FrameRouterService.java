package com.tianzhu.tianjing.route.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tianzhu.tianjing.route.domain.SceneRouteConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 帧路由分发服务
 *
 * 消费 tianjing.frame.production，根据 Redis 路由配置将帧转发至推理 Topic。
 * Sandbox 帧走 tianjing.frame.sandbox（由 traffic-mirror-service 写入，此处不重复）。
 *
 * 规范：CLAUDE.md §4.2、§8.1、§8.2
 */
@Slf4j
@Service
public class FrameRouterService {

    /**
     * 生产帧推理结果目标 Topic（规范：CLAUDE.md §8.1）
     * 注：实际推理结果由各推理服务写入 tianjing.infer.result.production
     * 此处路由分发服务将帧转发至内部推理调度 Topic
     */
    private static final String INFER_DISPATCH_TOPIC = "tianjing.frame.production.dispatch";

    private final RouteConfigCache routeConfigCache;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Counter routedCounter;
    private final Counter droppedCounter;

    public FrameRouterService(RouteConfigCache routeConfigCache,
                              KafkaTemplate<String, String> kafkaTemplate,
                              ObjectMapper objectMapper,
                              MeterRegistry meterRegistry) {
        this.routeConfigCache = routeConfigCache;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.routedCounter = Counter.builder("tianjing_frame_routed_total")
                .description("成功路由的帧数量")
                .register(meterRegistry);
        this.droppedCounter = Counter.builder("tianjing_frame_dropped_total")
                .description("因场景未激活被丢弃的帧数量")
                .register(meterRegistry);
    }

    /**
     * 消费生产帧，按场景路由分发至推理服务
     *
     * 规范：消费者组命名 {service-name}-{env}-cg（CLAUDE.md §8.2）
     */
    @KafkaListener(
            topics = "tianjing.frame.production",
            groupId = "route-dispatch-service-prod-cg",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onProductionFrame(ConsumerRecord<String, String> record) {
        String payload = record.value();

        try {
            ObjectNode frameMsg = (ObjectNode) objectMapper.readTree(payload);
            String sceneId = frameMsg.path("scene_id").asText(null);

            if (sceneId == null || sceneId.isBlank()) {
                log.warn("帧消息缺少 scene_id，丢弃", "offset", record.offset());
                droppedCounter.increment();
                return;
            }

            Optional<SceneRouteConfig> routeOpt = routeConfigCache.getRouteConfig(sceneId);
            if (routeOpt.isEmpty() || !routeOpt.get().isActive()) {
                // 场景未激活：静默丢弃（正常运营状态，某些摄像头场景会被禁用）
                log.debug("场景未激活，丢弃帧", "sceneId", sceneId);
                droppedCounter.increment();
                return;
            }

            SceneRouteConfig route = routeOpt.get();

            // 注入路由信息后转发
            frameMsg.put("plugin_id", route.getActiveModelVersionId());
            if (route.getRoi() != null) {
                ObjectNode roi = objectMapper.createObjectNode();
                roi.put("x", route.getRoi().getX());
                roi.put("y", route.getRoi().getY());
                roi.put("w", route.getRoi().getW());
                roi.put("h", route.getRoi().getH());
                frameMsg.set("roi", roi);
            }

            String enriched = objectMapper.writeValueAsString(frameMsg);
            // key = sceneId，保证同一场景的帧数据有序（规范：CLAUDE.md §8.1 分区规则）
            kafkaTemplate.send(INFER_DISPATCH_TOPIC, sceneId, enriched);
            routedCounter.increment();

            log.debug("帧路由成功",
                    "sceneId", sceneId,
                    "pluginId", route.getActiveModelVersionId(),
                    "frameId", frameMsg.path("frame_id").asText());

        } catch (Exception e) {
            log.error("帧路由处理异常，丢弃帧", "offset", record.offset(), "error", e.getMessage());
            droppedCounter.increment();
        }
    }
}
