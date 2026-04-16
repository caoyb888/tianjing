package com.tianzhu.tianjing.route.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianzhu.tianjing.route.domain.SceneRouteConfig;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 场景路由配置缓存（内存热缓存，热更新 <1s）
 *
 * 两层缓存策略：
 *   L1：本地 ConcurrentHashMap（零延迟查找）
 *   L2：Redis（tianjing:scene:active:{scene_id}，scene-config-service 写入）
 *
 * 热更新机制：
 *   - 每 500ms 定时扫描 Redis 中所有 scene key，同步至 L1
 *   - 路由分发时若 L1 缺失，则回源 L2（兜底）
 *
 * 规范：CLAUDE.md §4.2（route-dispatch-service 热更新 <1s 要求）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RouteConfigCache {

    private static final String REDIS_KEY_PREFIX = "tianjing:scene:active:";
    private static final String REDIS_SCAN_PATTERN = REDIS_KEY_PREFIX + "*";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    /** L1 本地内存缓存 */
    private final Map<String, SceneRouteConfig> localCache = new ConcurrentHashMap<>();

    /** 初始化后注册 Gauge 指标（规范：CLAUDE.md §14.2） */
    @jakarta.annotation.PostConstruct
    public void initMetrics() {
        Gauge.builder("tianjing_route_active_scenes", localCache, Map::size)
                .description("当前已激活的场景路由数量")
                .register(meterRegistry);
    }

    /**
     * 查找场景路由配置
     *
     * @param sceneId 场景 ID
     * @return Optional<SceneRouteConfig>，空表示场景未激活或不存在
     */
    public Optional<SceneRouteConfig> getRouteConfig(String sceneId) {
        // L1 命中
        SceneRouteConfig cached = localCache.get(sceneId);
        if (cached != null) {
            return Optional.of(cached);
        }

        // L1 未命中，回源 Redis（L2）
        return fetchFromRedis(sceneId);
    }

    /**
     * 定时热更新：每 500ms 全量同步 Redis → L1（保证热更新延迟 <1s）
     * 规范：CLAUDE.md §4.2
     */
    @Scheduled(fixedDelay = 500)
    public void refreshFromRedis() {
        try {
            Set<String> keys = redisTemplate.keys(REDIS_SCAN_PATTERN);
            if (keys == null || keys.isEmpty()) {
                localCache.clear();
                return;
            }

            // 构建新缓存快照
            Map<String, SceneRouteConfig> newCache = new ConcurrentHashMap<>();
            for (String key : keys) {
                String sceneId = key.substring(REDIS_KEY_PREFIX.length());
                fetchFromRedis(sceneId).ifPresent(config -> {
                    if (config.isActive()) {
                        newCache.put(sceneId, config);
                    }
                });
            }

            // 原子替换（逐 key 更新以减少锁范围）
            localCache.keySet().retainAll(newCache.keySet());
            localCache.putAll(newCache);

        } catch (Exception e) {
            // 更新失败不中断服务，继续使用旧缓存（降级）
            log.warn("路由缓存热更新失败，继续使用旧缓存", "error", e.getMessage());
        }
    }

    /**
     * 强制刷新单个场景路由（由管理接口触发）
     */
    public void forceRefresh(String sceneId) {
        fetchFromRedis(sceneId).ifPresentOrElse(
                config -> {
                    if (config.isActive()) {
                        localCache.put(sceneId, config);
                    } else {
                        localCache.remove(sceneId);
                    }
                    log.info("场景路由强制刷新完成", "sceneId", sceneId, "active", config.isActive());
                },
                () -> {
                    localCache.remove(sceneId);
                    log.info("场景路由已从缓存移除（Redis 不存在）", "sceneId", sceneId);
                }
        );
    }

    /**
     * 获取当前缓存中全部激活场景的快照（用于管理接口）
     */
    public Map<String, SceneRouteConfig> getActiveRoutes() {
        return Map.copyOf(localCache);
    }

    private Optional<SceneRouteConfig> fetchFromRedis(String sceneId) {
        try {
            String json = redisTemplate.opsForValue().get(REDIS_KEY_PREFIX + sceneId);
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            SceneRouteConfig config = objectMapper.readValue(json, SceneRouteConfig.class);
            return Optional.of(config);
        } catch (Exception e) {
            log.error("从 Redis 读取场景路由配置失败", "sceneId", sceneId, "error", e.getMessage());
            return Optional.empty();
        }
    }
}
