package com.tianzhu.tianjing.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianzhu.tianjing.common.exception.BusinessException;
import com.tianzhu.tianjing.common.exception.ErrorCode;
import com.tianzhu.tianjing.common.response.PageResult;
import com.tianzhu.tianjing.dashboard.domain.SandboxCompareReport;
import com.tianzhu.tianjing.dashboard.domain.SandboxSession;
import com.tianzhu.tianjing.dashboard.dto.SandboxPromoteRequest;
import com.tianzhu.tianjing.dashboard.dto.SandboxStartRequest;
import com.tianzhu.tianjing.dashboard.repository.SandboxCompareReportMapper;
import com.tianzhu.tianjing.dashboard.repository.SandboxSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Sandbox 实验室服务
 * 规范：API 接口规范 V3.1 §6.8（5 个接口）
 * CLAUDE.md §11 — Sandbox 规范具有最高约束力
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SandboxService {

    private final SandboxSessionMapper sessionMapper;
    private final SandboxCompareReportMapper reportMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public PageResult<SandboxSession> listSessions(int page, int size, String sceneId) {
        Page<SandboxSession> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<SandboxSession> wrapper = new LambdaQueryWrapper<SandboxSession>()
                .eq(sceneId != null, SandboxSession::getSceneId, sceneId)
                .orderByDesc(SandboxSession::getCreatedAt);
        var result = sessionMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result.getTotal(), page, size, result.getRecords());
    }

    public SandboxSession getSession(String sessionId) {
        SandboxSession session = sessionMapper.selectOne(new LambdaQueryWrapper<SandboxSession>()
                .eq(SandboxSession::getSessionId, sessionId));
        if (session == null) throw BusinessException.notFound(ErrorCode.SANDBOX_SESSION_NOT_FOUND);
        return session;
    }

    @Transactional
    public SandboxSession startSession(SandboxStartRequest request, String operator) {
        SandboxSession session = new SandboxSession();
        session.setSessionId("SS-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        session.setSceneId(request.sceneId());
        session.setProdModelId(request.productionModelVersionId());
        session.setCandidateModelId(request.experimentModelVersionId());
        session.setStatus("RUNNING");
        session.setStartAt(OffsetDateTime.now());
        session.setCreatedBy(operator);
        sessionMapper.insert(session);
        log.info("启动 Sandbox 会话 session_id={} scene_id={}", session.getSessionId(), session.getSceneId());
        return session;
    }

    @Transactional
    public SandboxSession stopSession(String sessionId, String operator) {
        SandboxSession session = getSession(sessionId);
        if (!"RUNNING".equals(session.getStatus())) {
            throw BusinessException.of(ErrorCode.RESOURCE_STATE_FORBIDDEN, "会话已停止");
        }
        OffsetDateTime now = OffsetDateTime.now();
        double hours = ChronoUnit.MINUTES.between(session.getStartAt(), now) / 60.0;
        session.setStatus("STOPPED");
        session.setEndAt(now);
        sessionMapper.updateById(session);
        log.info("停止 Sandbox 会话 session_id={} running_hours={}", sessionId, hours);
        return session;
    }

    public SandboxCompareReport getCompareReport(String sessionId) {
        return reportMapper.selectLatestBySession(sessionId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.SANDBOX_SESSION_NOT_FOUND));
    }

    /**
     * Sandbox 转正
     * 门禁检查：① 运行时长 ≥48h；② 精度提升 ≥2%；③ 资源消耗 ≤1.1x；④ P99 延迟 ≤1.2x；⑤ 2人审核
     * 规范：CLAUDE.md §11.3
     */
    @Transactional
    public Map<String, Object> promoteSession(String sessionId, SandboxPromoteRequest request, String operator) {
        SandboxSession session = getSession(sessionId);
        SandboxCompareReport report = reportMapper.selectLatestBySession(sessionId)
                .orElseThrow(() -> BusinessException.of(ErrorCode.SANDBOX_HOURS_INSUFFICIENT, "尚无对比报告"));

        Map<String, Object> gateDetails = new LinkedHashMap<>();
        List<String> failedGates = new ArrayList<>();

        // 门禁 ①：运行时长 ≥48h
        double runningHours = session.getStartAt() != null && session.getEndAt() != null
                ? ChronoUnit.MINUTES.between(session.getStartAt(), session.getEndAt()) / 60.0 : 0.0;
        boolean hoursOk = runningHours >= 48.0;
        gateDetails.put("sandbox_hours", Map.of(
                "passed", hoursOk,
                "current", runningHours,
                "required", 48.0
        ));
        if (!hoursOk) failedGates.add("sandbox_accuracy_check: 运行时长不足 48h");

        // 门禁 ②：精度提升 ≥2%
        boolean precisionOk = report.getPrecisionDelta() != null && report.getPrecisionDelta() >= 0.02;
        gateDetails.put("precision_delta", Map.of(
                "passed", precisionOk,
                "current", report.getPrecisionDelta(),
                "required", 0.02
        ));
        if (!precisionOk) failedGates.add("sandbox_accuracy_check: 精度提升不足 2%");

        // 门禁 ③：GPU 内存 ≤1.1x
        boolean memoryOk = report.getSandboxGpuMb() == null || report.getProdGpuMb() == null
                || report.getSandboxGpuMb() <= report.getProdGpuMb() * 1.1;
        gateDetails.put("resource_consumption", Map.of("passed", memoryOk));
        if (!memoryOk) failedGates.add("resource_consumption_check: GPU 内存超限");

        // 门禁 ④：P99 延迟 ≤1.2x
        boolean latencyOk = report.getSandboxP99Ms() == null || report.getProdP99Ms() == null
                || report.getSandboxP99Ms() <= report.getProdP99Ms() * 1.2;
        gateDetails.put("inference_latency", Map.of("passed", latencyOk));
        if (!latencyOk) failedGates.add("inference_latency_check: P99 延迟超限");

        if (!failedGates.isEmpty()) {
            try {
                throw BusinessException.of(ErrorCode.SANDBOX_GATE_NOT_PASS,
                        objectMapper.writeValueAsString(Map.of("failed_gates", failedGates, "details", gateDetails)));
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw BusinessException.of(ErrorCode.SANDBOX_GATE_NOT_PASS, failedGates.toString());
            }
        }

        // 所有门禁通过，进入人工审核阶段（触发 model-production-approve GitHub Environment）
        session.setStatus("COMPLETED");
        sessionMapper.updateById(session);

        log.info("Sandbox 转正门禁全部通过 session_id={} operator={}", sessionId, operator);
        return Map.of("status", "GATES_PASSED", "gate_details", gateDetails,
                "message", "门禁全部通过，已进入人工审核阶段（需 2 名 MODEL_REVIEWER 审批）");
    }
}
