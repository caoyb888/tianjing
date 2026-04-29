package com.tianzhu.tianjing.alarm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianzhu.tianjing.alarm.domain.AlarmRecord;
import com.tianzhu.tianjing.alarm.dto.InferResultMessage;
import com.tianzhu.tianjing.alarm.repository.AlarmRecordMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 告警判定核心服务
 *
 * 职责：接收推理结果 → 判断是否异常 → 确认连续帧数 → 创建 alarm_record → 发布告警 Kafka 消息
 *
 * 告警级别规则（CLAUDE.md 附录告警级别判定参考）：
 *   CRITICAL : confidence >= 0.9，连续帧 >= 3
 *   WARNING  : confidence >= 0.8，连续帧 >= 2
 *   INFO     : 单帧低置信度，不推送
 *
 * 分类插件（CLASSIFY-*）：正常类 "正常" 不触发告警；其余类视为异常
 * 检测插件（其他）      ：detections 非空且 top confidence >= 0.8 视为异常
 *
 * SECURITY: 所有外部推送操作前必须经过 SandboxInterceptor 校验（CLAUDE.md §11.1, §15 P0）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmJudgeService {

    /** CRITICAL 阈值：置信度 */
    private static final double CRITICAL_CONF  = 0.9;
    /** WARNING 阈值：置信度 */
    private static final double WARNING_CONF   = 0.8;
    /** 异常帧计数 Redis Key TTL（秒）：超时视为场景恢复正常 */
    private static final long ANOMALY_COUNT_TTL_SECONDS = 30;
    /** 默认告警冷却秒数（兜底值，优先读取场景 alarmConfig.suppress_seconds） */
    private static final long DEFAULT_SUPPRESS_SECONDS = 300;
    /** 默认 CRITICAL 连续帧数门槛（兜底值，优先读取场景 alarmConfig.confirm_frames） */
    private static final int DEFAULT_CRITICAL_FRAMES = 3;
    /** 已知的"正常"类名集合 */
    private static final Set<String> NORMAL_CLASS_NAMES = Set.of("正常", "normal", "ok");
    /** 场景告警配置 Redis key 前缀 */
    private static final String SCENE_CONFIG_KEY_PREFIX = "tianjing:scene:active:";

    private final AlarmRecordMapper alarmRecordMapper;
    private final SandboxInterceptor sandboxInterceptor;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    /** tianjing_alarm_total 计数器缓存，按 scene_id+level+is_sandbox 三维 */
    private final Map<String, Counter> alarmCounters = new ConcurrentHashMap<>();
    /** tianjing_alarm_intercepted_total 计数器缓存，按 scene_id */
    private final Map<String, Counter> interceptedCounters = new ConcurrentHashMap<>();

    /** 场景告警配置（confirm_frames + suppress_seconds），从 Redis 按场景读取 */
    private record SceneAlarmConfig(int criticalFrames, int warningFrames, long suppressSeconds) {}

    /**
     * 判定推理结果并在必要时产生告警记录。
     * 由 InferResultConsumer 调用，不应被其他地方直接调用。
     */
    public void judge(InferResultMessage result) {
        String sceneId   = result.getSceneId();
        String pluginId  = result.getPluginId();
        boolean sandbox  = result.isSandbox();

        // 1. 提取最高置信度异常类型
        AnomalyCandidate candidate = extractAnomaly(result);
        if (candidate == null) {
            // 本帧无异常：重置连续帧计数
            resetAnomalyCount(sceneId, "all");
            return;
        }

        double conf        = candidate.confidence;
        String anomalyType = candidate.anomalyType;

        // 2. 根据置信度确定候选告警级别
        if (conf < WARNING_CONF) {
            // 低置信度 → INFO，仅写数据库，不发 Kafka（CLAUDE.md 告警级别表）
            resetAnomalyCount(sceneId, anomalyType);
            return;
        }

        // 3. 读取场景告警配置（confirm_frames + suppress_seconds，来自 Redis 场景配置）
        SceneAlarmConfig alarmCfg = getSceneAlarmConfig(sceneId);

        // 4. 连续帧计数（Redis INCR + TTL 刷新）
        int frameCount = incrementAnomalyCount(sceneId, anomalyType);

        String level = determineLevel(conf, frameCount, alarmCfg);
        if (level == null) {
            // 帧数不足，尚未达到告警门槛
            return;
        }

        // 5. 冷却期检查：同一场景同一异常类型在 suppress_seconds 内不重复产生告警
        String cooldownKey = "alarm:cooldown:" + sceneId + ":" + anomalyType;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            log.debug("告警冷却中，跳过 scene_id={} anomaly_type={} level={}", sceneId, anomalyType, level);
            return;
        }

        // 6. SECURITY: Sandbox 拦截器
        //    - Sandbox 告警写入实验室 DB（alarm_level 前缀 SANDBOX_），禁止发 Kafka
        //    - 生产告警正常写入并发 Kafka（CLAUDE.md §11.1, §15 P0）
        boolean allowPush = sandboxInterceptor.allowExternalPush(sandbox);
        String actualLevel = (sandbox && allowPush == false)
                ? "SANDBOX_" + level
                : level;

        // 6. 构建并写入告警记录
        AlarmRecord record = buildAlarmRecord(result, anomalyType, conf, actualLevel, frameCount);
        alarmRecordMapper.insert(record);
        log.info("告警记录已创建 alarm_id={} scene_id={} level={} anomaly={} conf={} is_sandbox={}",
                record.getAlarmId(), sceneId, actualLevel, anomalyType, conf, sandbox);

        // 7. Prometheus 指标（CLAUDE.md §14.2）
        alarmCounter(sceneId, actualLevel, sandbox).increment();
        if (!allowPush) {
            interceptedCounter(sceneId).increment();
        }

        // 8. 重置连续帧计数 + 设置场景级冷却期
        resetAnomalyCount(sceneId, anomalyType);
        redisTemplate.opsForValue().set(cooldownKey, "1", alarmCfg.suppressSeconds(), TimeUnit.SECONDS);

        // 9. 生产告警发布 Kafka（Sandbox 告警绝不到达此处，CLAUDE.md §15 P0）
        if (allowPush) {
            publishAlarmMessage(record);
        }
    }

    // ── 异常提取 ───────────────────────────────────────────────────────────────

    private AnomalyCandidate extractAnomaly(InferResultMessage result) {
        // 分类插件（CLASSIFY-*）：classifications 优先
        List<InferResultMessage.Classification> classifications = result.getClassifications();
        if (!CollectionUtils.isEmpty(classifications)) {
            // 取置信度最高的分类结果
            InferResultMessage.Classification top = classifications.stream()
                    .max((a, b) -> Double.compare(a.getConfidence(), b.getConfidence()))
                    .orElse(null);
            if (top == null) return null;
            String className = top.getClassName();
            // 正常类不触发告警
            if (NORMAL_CLASS_NAMES.stream().anyMatch(n -> n.equalsIgnoreCase(className))) {
                return null;
            }
            return new AnomalyCandidate(className, top.getConfidence());
        }

        // 检测插件（YOLO 等）：detections 非空视为异常
        List<InferResultMessage.Detection> detections = result.getDetections();
        if (!CollectionUtils.isEmpty(detections)) {
            InferResultMessage.Detection top = detections.stream()
                    .max((a, b) -> Double.compare(a.getConfidence(), b.getConfidence()))
                    .orElse(null);
            if (top == null) return null;
            return new AnomalyCandidate(top.getClassName(), top.getConfidence());
        }

        return null;
    }

    // ── 连续帧计数（Redis）────────────────────────────────────────────────────

    private int incrementAnomalyCount(String sceneId, String anomalyType) {
        String key = "alarm:anomaly_count:" + sceneId + ":" + anomalyType;
        Long count = redisTemplate.opsForValue().increment(key);
        // 每次 INCR 刷新 TTL：超过 TTL 无新帧视为场景已恢复
        redisTemplate.expire(key, ANOMALY_COUNT_TTL_SECONDS, TimeUnit.SECONDS);
        return count == null ? 1 : count.intValue();
    }

    private void resetAnomalyCount(String sceneId, String anomalyType) {
        if ("all".equals(anomalyType)) {
            // 无法精确重置所有 key，不执行（TTL 自然过期）
            return;
        }
        redisTemplate.delete("alarm:anomaly_count:" + sceneId + ":" + anomalyType);
    }

    // ── 告警级别判定 ───────────────────────────────────────────────────────────

    private String determineLevel(double conf, int frameCount, SceneAlarmConfig cfg) {
        // CRITICAL 区间（conf >= 0.9）：等待 criticalFrames，不经过 WARNING 直接升级
        if (conf >= CRITICAL_CONF) {
            return frameCount >= cfg.criticalFrames() ? "CRITICAL" : null;
        }
        // WARNING 区间（0.8 <= conf < 0.9）：等待 warningFrames
        if (conf >= WARNING_CONF) {
            return frameCount >= cfg.warningFrames() ? "WARNING" : null;
        }
        return null;
    }

    // ── 场景告警配置（Redis 按场景读取，低代码编排器改后即时生效）────────────────

    private SceneAlarmConfig getSceneAlarmConfig(String sceneId) {
        try {
            String json = redisTemplate.opsForValue().get(SCENE_CONFIG_KEY_PREFIX + sceneId);
            if (json == null) return defaultAlarmConfig();
            com.fasterxml.jackson.databind.JsonNode alarmCfg =
                    objectMapper.readTree(json).path("alarmConfig");
            if (alarmCfg.isMissingNode()) return defaultAlarmConfig();
            int confirmFrames  = alarmCfg.path("confirm_frames").asInt(DEFAULT_CRITICAL_FRAMES);
            long suppressSec   = alarmCfg.path("suppress_seconds").asLong(DEFAULT_SUPPRESS_SECONDS);
            // WARNING 比 CRITICAL 宽松一帧，最低 1
            int warningFrames  = Math.max(1, confirmFrames - 1);
            long effective     = suppressSec > 0 ? suppressSec : DEFAULT_SUPPRESS_SECONDS;
            return new SceneAlarmConfig(confirmFrames, warningFrames, effective);
        } catch (Exception e) {
            log.debug("读取场景告警配置失败，使用默认值 scene_id={}", sceneId, e);
            return defaultAlarmConfig();
        }
    }

    private SceneAlarmConfig defaultAlarmConfig() {
        return new SceneAlarmConfig(DEFAULT_CRITICAL_FRAMES, DEFAULT_CRITICAL_FRAMES - 1, DEFAULT_SUPPRESS_SECONDS);
    }

    // ── 构建告警记录 ───────────────────────────────────────────────────────────

    private AlarmRecord buildAlarmRecord(InferResultMessage result, String anomalyType,
                                         double conf, String level, int confirmFrames) {
        AlarmRecord r = new AlarmRecord();
        r.setAlarmId("ALM-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        r.setSceneId(result.getSceneId());
        r.setFactoryCode(StringUtils.hasText(result.getFactory()) ? result.getFactory() : "UNKNOWN");
        r.setAlarmLevel(level);
        r.setAnomalyType(anomalyType);
        r.setConfidence(conf);
        r.setImageUrl(StringUtils.hasText(result.getImageUrl()) ? result.getImageUrl() : "");
        r.setFrameId(result.getFrameId());
        r.setPluginId(result.getPluginId());
        // model_version_id 为 NOT NULL；推理结果消息中无版本信息，用 plugin_id 占位
        r.setModelVersionId(result.getPluginId());
        r.setConfirmFrames(confirmFrames);
        r.setIsSandbox(result.isSandbox());
        // SECURITY: Sandbox 告警状态标记 INTERCEPTED，禁止 PENDING（CLAUDE.md §11.1）
        r.setPushStatus(result.isSandbox() ? "INTERCEPTED" : "PENDING");
        OffsetDateTime now = OffsetDateTime.now();
        r.setAlarmAt(now);
        r.setCreatedAt(now);  // FieldFill.INSERT 无 MetaObjectHandler，显式赋值
        return r;
    }

    // ── Kafka 告警推送（仅生产告警，CLAUDE.md §8.1）─────────────────────────────

    private void publishAlarmMessage(AlarmRecord record) {
        String level = record.getAlarmLevel();
        String topic = switch (level) {
            case "CRITICAL" -> "tianjing.alarm.critical";
            case "WARNING"  -> "tianjing.alarm.warning";
            default         -> "tianjing.alarm.info";
        };
        try {
            String msg = objectMapper.writeValueAsString(java.util.Map.of(
                    "alarm_id",    record.getAlarmId(),
                    "scene_id",    record.getSceneId(),
                    "factory",     record.getFactoryCode(),
                    "alarm_level", level,
                    "anomaly_type", record.getAnomalyType(),
                    "confidence",  record.getConfidence(),
                    "image_url",   record.getImageUrl() != null ? record.getImageUrl() : "",
                    "is_sandbox",  false,
                    "timestamp_ms", record.getAlarmAt().toInstant().toEpochMilli()
            ));
            kafkaTemplate.send(topic, record.getSceneId(), msg);
            log.info("告警消息已发布 topic={} alarm_id={}", topic, record.getAlarmId());
        } catch (Exception e) {
            log.error("告警 Kafka 发布失败 alarm_id={}", record.getAlarmId(), e);
        }
    }

    // ── Prometheus 指标辅助 ────────────────────────────────────────────────────

    private Counter alarmCounter(String sceneId, String level, boolean isSandbox) {
        String key = sceneId + "|" + level + "|" + isSandbox;
        return alarmCounters.computeIfAbsent(key, k ->
                Counter.builder("tianjing_alarm_total")
                        .description("告警触发计数")
                        .tag("scene_id", sceneId)
                        .tag("level", level)
                        .tag("is_sandbox", String.valueOf(isSandbox))
                        .register(meterRegistry));
    }

    private Counter interceptedCounter(String sceneId) {
        return interceptedCounters.computeIfAbsent(sceneId, k ->
                Counter.builder("tianjing_alarm_intercepted_total")
                        .description("Sandbox 告警拦截计数")
                        .tag("scene_id", sceneId)
                        .register(meterRegistry));
    }

    // ── 内部值对象 ─────────────────────────────────────────────────────────────

    private record AnomalyCandidate(String anomalyType, double confidence) {}
}
