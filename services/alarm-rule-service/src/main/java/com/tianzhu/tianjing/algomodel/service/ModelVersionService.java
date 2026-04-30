package com.tianzhu.tianjing.algomodel.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianzhu.tianjing.algomodel.domain.ModelVersion;
import com.tianzhu.tianjing.algomodel.dto.ModelApproveRequest;
import com.tianzhu.tianjing.algomodel.dto.ModelRegisterRequest;
import com.tianzhu.tianjing.algomodel.repository.ModelVersionMapper;
import com.tianzhu.tianjing.common.exception.BusinessException;
import com.tianzhu.tianjing.common.exception.ErrorCode;
import com.tianzhu.tianjing.common.response.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 模型版本管理服务
 * 规范：API 接口规范 V3.1 §6.6（5 个接口）
 *
 * 状态机：STAGING → SANDBOX_VALIDATING → REVIEWING → PRODUCTION → DEPRECATED
 * 四眼原则：审核人 ≠ 提交人（返回 4004）
 * 蓝绿切换：新版本 PRODUCTION 上线时，旧版本自动 DEPRECATED
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelVersionService {

    private final ModelVersionMapper modelVersionMapper;

    @Value("${tianjing.inference.proxy-url:http://localhost:8092}")
    private String inferenceProxyUrl;

    /** Sandbox 最低验证时长（小时）。0 = 关闭门禁（Sprint 3 测试模式）。生产默认 48。 */
    @Value("${tianjing.sandbox.min-hours:48}")
    private int minSandboxHours;

    public PageResult<ModelVersion> listVersions(int page, int size, String pluginId, String status) {
        Page<ModelVersion> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<ModelVersion> wrapper = new LambdaQueryWrapper<ModelVersion>()
                .eq(pluginId != null, ModelVersion::getPluginId, pluginId)
                .eq(status != null, ModelVersion::getStatus, status)
                .orderByDesc(ModelVersion::getCreatedAt);
        var result = modelVersionMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result.getTotal(), page, size, result.getRecords());
    }

    public ModelVersion getVersion(String modelVersionId) {
        ModelVersion mv = modelVersionMapper.selectOne(new LambdaQueryWrapper<ModelVersion>()
                .eq(ModelVersion::getVersionId, modelVersionId));
        if (mv == null) throw BusinessException.notFound(ErrorCode.MODEL_VERSION_NOT_FOUND);
        return mv;
    }

    @Transactional
    public ModelVersion registerVersion(ModelRegisterRequest request, String operator) {
        ModelVersion mv = new ModelVersion();
        mv.setVersionId("MV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        mv.setPluginId(request.pluginId());
        mv.setMlflowRunId(request.mlflowRunId());
        mv.setModelPath(request.modelArtifactUrl());
        mv.setTrainJobId(request.trainingJobId());
        // 训练流水线目前固定导出 ONNX 格式；如后续支持 TensorRT 可从请求体扩展此字段
        mv.setExportFormat("ONNX");
        mv.setStatus("STAGING");
        mv.setCreatedBy(operator);
        mv.setUpdatedBy(operator);
        OffsetDateTime now = OffsetDateTime.now();
        mv.setCreatedAt(now);
        mv.setUpdatedAt(now);
        modelVersionMapper.insert(mv);
        log.info("注册模型版本 version_id={} plugin_id={} operator={}", mv.getVersionId(), mv.getPluginId(), operator);
        return mv;
    }

    /**
     * 提交审核（STAGING / SANDBOX_VALIDATING → REVIEWING）
     *
     * 门禁：SANDBOX_VALIDATING 状态须满足最低 Sandbox 验证时长（tianjing.sandbox.min-hours，默认 48h）。
     * Sprint 3 测试绕过：将 TIANJING_SANDBOX_MIN_HOURS=0 注入环境，min-hours 降为 0，门禁关闭。
     */
    @Transactional
    public ModelVersion submitReview(String modelVersionId, String operator) {
        ModelVersion mv = getVersion(modelVersionId);
        mv.setUpdatedBy(operator);
        if (!"STAGING".equals(mv.getStatus()) && !"SANDBOX_VALIDATING".equals(mv.getStatus())) {
            throw BusinessException.of(ErrorCode.RESOURCE_STATE_FORBIDDEN,
                    "当前状态 " + mv.getStatus() + " 不允许提交审核");
        }
        // SANDBOX_VALIDATING 状态需满足最低验证时长门禁
        if ("SANDBOX_VALIDATING".equals(mv.getStatus()) && minSandboxHours > 0) {
            int hours = mv.getSandboxHours() != null ? mv.getSandboxHours() : 0;
            if (hours < minSandboxHours) {
                throw BusinessException.of(ErrorCode.SANDBOX_HOURS_INSUFFICIENT,
                        "当前 Sandbox 验证时长 " + hours + "h 未达到最低要求 " + minSandboxHours + "h");
            }
        }
        mv.setStatus("REVIEWING");
        mv.setSubmittedBy(operator);
        modelVersionMapper.updateById(mv);
        log.info("提交模型版本审核 version_id={} operator={}", modelVersionId, operator);
        return mv;
    }

    /**
     * 审核通过（四眼原则：审核人 ≠ 提交人）
     * REVIEWING → PRODUCTION，旧版本 → DEPRECATED
     */
    @Transactional
    public ModelVersion approve(String modelVersionId, ModelApproveRequest request, String reviewer) {
        ModelVersion mv = getVersion(modelVersionId);

        if (!"REVIEWING".equals(mv.getStatus())) {
            throw BusinessException.of(ErrorCode.RESOURCE_STATE_FORBIDDEN, "当前状态不允许审核操作");
        }

        // 四眼原则：审核人不能是提交人（submittedBy 为空时降级比较 createdBy）
        String submitter = mv.getSubmittedBy() != null ? mv.getSubmittedBy() : mv.getCreatedBy();
        if (reviewer.equals(submitter)) {
            throw BusinessException.forbidden(ErrorCode.FOUR_EYES_VIOLATION);
        }

        mv.setApprovedBy(reviewer);
        mv.setApprovedAt(OffsetDateTime.now());
        mv.setUpdatedBy(reviewer);

        if (request.approved()) {
            // 蓝绿切换：将旧生产版本标记为 DEPRECATED，同步写入废弃时间戳
            modelVersionMapper.update(new LambdaUpdateWrapper<ModelVersion>()
                    .eq(ModelVersion::getPluginId, mv.getPluginId())
                    .eq(ModelVersion::getStatus, "PRODUCTION")
                    .set(ModelVersion::getStatus, "DEPRECATED")
                    .set(ModelVersion::getDeprecatedAt, OffsetDateTime.now()));

            mv.setStatus("PRODUCTION");
            mv.setDeployedAt(OffsetDateTime.now());
            log.info("模型版本审核通过并上线 version_id={} reviewer={}", modelVersionId, reviewer);
        } else {
            mv.setStatus("STAGING");
            log.info("模型版本审核拒绝 version_id={} reviewer={}", modelVersionId, reviewer);
        }

        modelVersionMapper.updateById(mv);

        // 审核通过后通知 GPU 推理服务热加载（在事务提交后执行，失败不回滚）
        if (request.approved()) {
            triggerInferenceReload(mv);
        }

        return mv;
    }

    /**
     * 通知 GPU 推理服务热加载新上线模型。
     * 失败只记录日志，不阻塞审核流程（模型已写库，管理员可手动触发重载）。
     */
    private void triggerInferenceReload(ModelVersion mv) {
        if (mv.getModelPath() == null || mv.getModelPath().isBlank()) {
            log.warn("模型版本无 model_path，跳过推理热加载 version_id={}", mv.getVersionId());
            return;
        }
        try {
            RestClient client = RestClient.create();
            Map<String, String> body = Map.of(
                    "model_path",       mv.getModelPath(),
                    "model_version_id", mv.getVersionId()
            );
            client.post()
                    .uri(inferenceProxyUrl + "/reload")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("推理服务热加载触发成功 version_id={} model_path={}", mv.getVersionId(), mv.getModelPath());
        } catch (Exception e) {
            log.warn("推理服务热加载触发失败（不影响审核结果，可手动重载）version_id={} error={}",
                    mv.getVersionId(), e.getMessage());
        }
    }

    @Transactional
    public void deprecate(String modelVersionId, String operator) {
        ModelVersion mv = getVersion(modelVersionId);
        mv.setStatus("DEPRECATED");
        mv.setDeprecatedAt(OffsetDateTime.now());
        mv.setUpdatedBy(operator);
        modelVersionMapper.updateById(mv);
        log.info("废弃模型版本 version_id={} operator={}", modelVersionId, operator);
    }
}
