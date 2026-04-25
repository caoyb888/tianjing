package com.tianzhu.tianjing.alarm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianzhu.tianjing.alarm.domain.AlarmRecord;
import com.tianzhu.tianjing.alarm.dto.InferResultMessage;
import com.tianzhu.tianjing.alarm.repository.AlarmRecordMapper;
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
import java.util.Set;
import java.util.UUID;
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

    private static final String NORMAL_CLASS = "正常";

    /** CRITICAL 阈值：置信度 */
    private static final double CRITICAL_CONF  = 0.9;
    /** WARNING 阈值：置信度 */
    private static final double WARNING_CONF   = 0.8;
    /** CRITICAL 连续帧数门槛 */
    private static final int CRITICAL_FRAMES   = 3;
    /** WARNING 连续帧数门槛 */
    private static final int WARNING_FRAMES    = 2;
    /** 异常帧计数 Redis Key TTL（秒）：超时视为场景恢复正常 */
    private static final long ANOMALY_COUNT_TTL_SECONDS = 30;
    /** 告警冷却 Redis Key TTL（秒）：同一场景同一异常类型在冷却期内不重复产生告警 */
    private static final long ALARM_COOLDOWN_TTL_SECONDS = 60;

    /** 已知的"正常"类名集合（兼容后续可能新增的正常态描述词） */
    private static final Set<String> NORMAL_CLASS_NAMES = Set.of("正常", "normal", "ok");

    private final AlarmRecordMapper alarmRecordMapper;
    private final SandboxInterceptor sandboxInterceptor;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${tianjing.alarm.confirm-frames:3}")
    private int configCriticalFrames;

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

        // 3. 连续帧计数（Redis INCR + TTL 刷新）
        int frameCount = incrementAnomalyCount(sceneId, anomalyType);

        String level = determineLevel(conf, frameCount);
        if (level == null) {
            // 帧数不足，尚未达到告警门槛
            return;
        }

        // 4. 冷却期检查：同一场景同一异常类型 60s 内不重复产生告警
        String cooldownKey = "alarm:cooldown:" + sceneId + ":" + anomalyType;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            log.debug("告警冷却中，跳过 scene_id={} anomaly_type={} level={}", sceneId, anomalyType, level);
            return;
        }

        // 5. SECURITY: Sandbox 拦截器
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

        // 7. 重置连续帧计数 + 设置冷却期
        resetAnomalyCount(sceneId, anomalyType);
        redisTemplate.opsForValue().set(cooldownKey, "1", ALARM_COOLDOWN_TTL_SECONDS, TimeUnit.SECONDS);

        // 8. 生产告警发布 Kafka（Sandbox 告警绝不到达此处，CLAUDE.md §15 P0）
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

    private String determineLevel(double conf, int frameCount) {
        if (conf >= CRITICAL_CONF && frameCount >= CRITICAL_FRAMES) return "CRITICAL";
        if (conf >= WARNING_CONF  && frameCount >= WARNING_FRAMES)  return "WARNING";
        return null;  // 帧数不足
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
        r.setImageUrl(result.getImageUrl());
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

    // ── 内部值对象 ─────────────────────────────────────────────────────────────

    private record AnomalyCandidate(String anomalyType, double confidence) {}
}
