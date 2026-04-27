package com.tianzhu.tianjing.alarm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianzhu.tianjing.alarm.domain.AlarmRecord;
import com.tianzhu.tianjing.alarm.dto.AlarmDetailDTO;
import com.tianzhu.tianjing.alarm.dto.AlarmFeedbackRequest;
import com.tianzhu.tianjing.alarm.dto.AlarmListItemDTO;
import com.tianzhu.tianjing.alarm.dto.AlarmQueryParams;
import com.tianzhu.tianjing.alarm.repository.AlarmRecordMapper;
import com.tianzhu.tianjing.common.exception.BusinessException;
import com.tianzhu.tianjing.common.exception.ErrorCode;
import com.tianzhu.tianjing.common.response.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 告警管理业务服务
 * 规范：API 接口规范 V3.1 §6.7
 * 扩展：实时告警大屏开发优化计划.md Phase 1（支持 levels 多选过滤）
 *
 * SECURITY: 所有外部推送操作必须经过 SandboxInterceptor 校验
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmService {

    private final AlarmRecordMapper alarmMapper;
    private final SandboxInterceptor sandboxInterceptor;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    // ==============================
    // 查询告警列表
    // ==============================

    public PageResult<AlarmListItemDTO> listAlarms(AlarmQueryParams params) {
        Page<AlarmRecord> pageParam = new Page<>(params.page(), params.size());
        LambdaQueryWrapper<AlarmRecord> wrapper = new LambdaQueryWrapper<AlarmRecord>()
                .eq(params.sceneId() != null, AlarmRecord::getSceneId, params.sceneId())
                .eq(params.alarmLevel() != null, AlarmRecord::getAlarmLevel, params.alarmLevel())
                .eq(params.factoryCode() != null, AlarmRecord::getFactoryCode, params.factoryCode())
                .eq(params.isSandbox() != null, AlarmRecord::getIsSandbox, params.isSandbox())
                .ge(params.startTime() != null, AlarmRecord::getAlarmAt, params.startTime())
                .le(params.endTime() != null, AlarmRecord::getAlarmAt, params.endTime())
                .orderByDesc(AlarmRecord::getAlarmAt);

        // 处理 levels 多选过滤（优先级高于单选 alarmLevel）
        if (!CollectionUtils.isEmpty(params.levels())) {
            wrapper.in(AlarmRecord::getAlarmLevel, params.levels());
        }

        var result = alarmMapper.selectPage(pageParam, wrapper);
        List<AlarmListItemDTO> items = result.getRecords().stream().map(this::toListItemDTO).toList();
        return PageResult.of(result.getTotal(), params.page(), params.size(), items);
    }

    // ==============================
    // 查询单条告警
    // ==============================

    public AlarmDetailDTO getAlarmDetail(String alarmId) {
        AlarmRecord alarm = getAlarmEntity(alarmId);
        return toDetailDTO(alarm);
    }

    AlarmRecord getAlarmEntity(String alarmId) {
        AlarmRecord alarm = alarmMapper.selectOne(new LambdaQueryWrapper<AlarmRecord>()
                .eq(AlarmRecord::getAlarmId, alarmId));
        if (alarm == null) {
            throw BusinessException.notFound(ErrorCode.ALARM_NOT_FOUND);
        }
        return alarm;
    }

    // ==============================
    // 告警反馈（人工复核结果）
    // ==============================

    @Transactional
    public void submitFeedback(String alarmId, AlarmFeedbackRequest request, String operator) {
        AlarmRecord alarm = getAlarmEntity(alarmId);

        if (StringUtils.hasText(alarm.getFeedbackStatus())) {
            throw BusinessException.of(ErrorCode.ALARM_FEEDBACK_DUPLICATE);
        }

        // 记录人工处置类型（TRUE_POSITIVE / FALSE_POSITIVE / FALSE_NEGATIVE）
        alarm.setFeedbackStatus(request.feedbackType());
        alarm.setPushStatus("FEEDBACK_DONE");
        alarm.setResolvedAt(OffsetDateTime.now());
        alarmMapper.updateById(alarm);

        // 向漂移监测服务发送反馈消息（tianjing.drift.feedback）
        // feedback_type：TRUE_POSITIVE / FALSE_POSITIVE / FALSE_NEGATIVE
        // drift-monitor-service 根据三分类计算 Precision / Recall，驱动重训练门禁
        String feedbackMsg = String.format(
                "{\"alarm_id\":\"%s\",\"scene_id\":\"%s\",\"feedback_type\":\"%s\",\"operator\":\"%s\"}",
                alarmId, alarm.getSceneId(), request.feedbackType(), operator);
        kafkaTemplate.send("tianjing.drift.feedback", alarm.getSceneId(), feedbackMsg);

        log.info("提交告警反馈 alarm_id={} feedback_type={} operator={}", alarmId, request.feedbackType(), operator);
    }

    // ==============================
    // 重推告警（仅限 FAILED 状态）
    // ==============================

    @Transactional
    public void retryPush(String alarmId) {
        AlarmRecord alarm = getAlarmEntity(alarmId);

        if (!"FAILED".equals(alarm.getPushStatus())) {
            throw BusinessException.of(ErrorCode.ALARM_NOT_FAILED);
        }

        // SECURITY: 重推前必须经过 Sandbox 拦截器校验
        sandboxInterceptor.assertNotSandbox(alarm.getIsSandbox(), "retryPush alarm_id=" + alarmId);

        alarm.setPushStatus("PENDING");
        alarmMapper.updateById(alarm);

        // 重新发布到告警 Topic（notification-service 消费后推送）
        String level = alarm.getAlarmLevel();
        String topic = "CRITICAL".equals(level) ? "tianjing.alarm.critical"
                : "WARNING".equals(level) ? "tianjing.alarm.warning"
                : "tianjing.alarm.info";

        kafkaTemplate.send(topic, alarm.getSceneId(), buildAlarmMessage(alarm));
        log.info("重推告警 alarm_id={} topic={}", alarmId, topic);
    }

    // ── DTO 转换 ───────────────────────────────────────────────────────────────

    private AlarmDetailDTO toDetailDTO(AlarmRecord r) {
        return new AlarmDetailDTO(
                r.getAlarmId(),
                r.getSceneId(),
                r.getFactoryCode(),
                r.getAlarmLevel(),
                r.getAnomalyType(),
                r.getConfidence() != null ? r.getConfidence() : 0.0,
                toHttpUrl(r.getImageUrl()),
                parseBboxJson(r.getBboxJson()),
                Boolean.TRUE.equals(r.getIsSandbox()),
                r.getPushStatus(),
                r.getFeedbackStatus(),
                r.getAlarmAt() != null ? r.getAlarmAt().toString() : null,
                r.getResolvedAt() != null ? r.getResolvedAt().toString() : null,
                r.getPluginId()
        );
    }

    private AlarmListItemDTO toListItemDTO(AlarmRecord r) {
        return new AlarmListItemDTO(
                r.getAlarmId(),
                r.getSceneId(),
                r.getFactoryCode(),
                r.getAlarmLevel(),
                r.getAnomalyType(),
                r.getConfidence() != null ? r.getConfidence() : 0.0,
                toHttpUrl(r.getImageUrl()),
                Boolean.TRUE.equals(r.getIsSandbox()),
                r.getPushStatus(),
                r.getFeedbackStatus(),
                r.getAlarmAt() != null ? r.getAlarmAt().toString() : null
        );
    }

    /** minio://{bucket}/{path} → /minio-frames/{bucket}/{path} */
    private String toHttpUrl(String minioUrl) {
        if (minioUrl == null) return null;
        if (minioUrl.startsWith("minio://")) {
            return "/minio-frames/" + minioUrl.substring("minio://".length());
        }
        return minioUrl;
    }

    /** bboxJson（JSONB 字符串）→ DetectionDTO 列表；分类插件返回空列表 */
    @SuppressWarnings("unchecked")
    private List<AlarmDetailDTO.DetectionDTO> parseBboxJson(String bboxJson) {
        if (!StringUtils.hasText(bboxJson)) return Collections.emptyList();
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(bboxJson, new TypeReference<>() {});
            return raw.stream().map(m -> {
                Map<String, Object> bbox = (Map<String, Object>) m.getOrDefault("bbox", Map.of());
                return new AlarmDetailDTO.DetectionDTO(
                        ((Number) m.getOrDefault("class_id", 0)).intValue(),
                        (String) m.getOrDefault("class_name", ""),
                        ((Number) m.getOrDefault("confidence", 0.0)).doubleValue(),
                        new AlarmDetailDTO.BBoxDTO(
                                ((Number) bbox.getOrDefault("x1", 0)).floatValue(),
                                ((Number) bbox.getOrDefault("y1", 0)).floatValue(),
                                ((Number) bbox.getOrDefault("x2", 0)).floatValue(),
                                ((Number) bbox.getOrDefault("y2", 0)).floatValue()
                        )
                );
            }).toList();
        } catch (Exception e) {
            log.warn("bboxJson 解析失败，返回空列表 bboxJson={}", bboxJson, e);
            return Collections.emptyList();
        }
    }

    private String buildAlarmMessage(AlarmRecord alarm) {
        return String.format(
                "{\"alarm_id\":\"%s\",\"scene_id\":\"%s\",\"alarm_level\":\"%s\"," +
                "\"is_sandbox\":%b,\"timestamp_ms\":%d}",
                alarm.getAlarmId(), alarm.getSceneId(), alarm.getAlarmLevel(),
                alarm.getIsSandbox(), alarm.getAlarmAt().toInstant().toEpochMilli());
    }
}
