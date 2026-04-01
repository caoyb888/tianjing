package com.tianzhu.tianjing.aggregate.service;

import com.tianzhu.tianjing.aggregate.dto.InferResultMessage;
import com.tianzhu.tianjing.aggregate.repository.TDengineWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 推理结果聚合服务
 *
 * 职责：接收推理结果，校验 is_sandbox 标签，写入 TDengine 时序数据库
 *
 * 安全约束（CLAUDE.md §7.2、§15 P2）：
 * - is_sandbox=true 的结果只写入 tianjing_sandbox 数据库
 * - is_sandbox=false 的结果写入 tianjing_prod 数据库
 * - 禁止在写入前修改 is_sandbox 标签的值
 *
 * 数据保留策略（CLAUDE.md §7.2）：
 * - 生产推理结果：180 天（TDengine TTL 由 DBA 配置）
 * - Sandbox 推理结果：30 天
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResultAggregateService {

    private final TDengineWriter tdengineWriter;

    /**
     * 聚合处理生产推理结果（来自 tianjing.infer.result.production）
     * is_sandbox 必须为 false；若消息异常携带 true，拒绝写入并告警
     */
    public void handleProductionResult(InferResultMessage result) {
        // SECURITY: 防御性校验 — 生产 Topic 的消息 is_sandbox 不得为 true
        if (result.isSandbox()) {
            log.error("安全违规：生产 Topic 收到 is_sandbox=true 的消息，拒绝写入 scene_id={} frame_id={}",
                    result.getSceneId(), result.getFrameId());
            return;
        }
        writeWithMetrics(result);
    }

    /**
     * 聚合处理 Sandbox 推理结果（来自 tianjing.infer.result.sandbox）
     * is_sandbox 必须为 true；若消息异常携带 false，强制修正并告警
     */
    public void handleSandboxResult(InferResultMessage result) {
        // SECURITY: Sandbox Topic 的消息 is_sandbox 必须为 true
        if (!result.isSandbox()) {
            log.error("安全违规：Sandbox Topic 收到 is_sandbox=false 的消息，强制修正后写入 scene_id={} frame_id={}",
                    result.getSceneId(), result.getFrameId());
            result.setSandbox(true);  // 强制修正，确保 TDengine 标签正确
        }
        writeWithMetrics(result);
    }

    private void writeWithMetrics(InferResultMessage result) {
        try {
            tdengineWriter.write(result);
            log.debug("推理结果写入 TDengine 成功 scene_id={} frame_id={} is_sandbox={} detections={}",
                    result.getSceneId(), result.getFrameId(), result.isSandbox(),
                    result.getDetections() == null ? 0 : result.getDetections().size());
        } catch (Exception e) {
            log.error("推理结果写入 TDengine 失败 scene_id={} frame_id={} is_sandbox={}",
                    result.getSceneId(), result.getFrameId(), result.isSandbox(), e);
        }
    }
}
