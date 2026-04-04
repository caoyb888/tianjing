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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
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
        mv.setStatus("STAGING");
        mv.setCreatedBy(operator);
        mv.setUpdatedBy(operator);
        modelVersionMapper.insert(mv);
        log.info("注册模型版本 version_id={} plugin_id={} operator={}", mv.getVersionId(), mv.getPluginId(), operator);
        return mv;
    }

    /**
     * 提交审核（STAGING / SANDBOX_VALIDATING → REVIEWING）
     */
    @Transactional
    public ModelVersion submitReview(String modelVersionId, String operator) {
        ModelVersion mv = getVersion(modelVersionId);
        mv.setUpdatedBy(operator);
        if (!"STAGING".equals(mv.getStatus()) && !"SANDBOX_VALIDATING".equals(mv.getStatus())) {
            throw BusinessException.of(ErrorCode.RESOURCE_STATE_FORBIDDEN,
                    "当前状态 " + mv.getStatus() + " 不允许提交审核");
        }
        mv.setStatus("REVIEWING");
        mv.setSubmittedBy(operator);
        modelVersionMapper.updateById(mv);
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
            // 蓝绿切换：将旧生产版本标记为 DEPRECATED
            modelVersionMapper.update(new LambdaUpdateWrapper<ModelVersion>()
                    .eq(ModelVersion::getPluginId, mv.getPluginId())
                    .eq(ModelVersion::getStatus, "PRODUCTION")
                    .set(ModelVersion::getStatus, "DEPRECATED"));

            mv.setStatus("PRODUCTION");
            mv.setDeployedAt(OffsetDateTime.now());
            log.info("模型版本审核通过并上线 version_id={} reviewer={}", modelVersionId, reviewer);
        } else {
            mv.setStatus("STAGING");
            log.info("模型版本审核拒绝 version_id={} reviewer={}", modelVersionId, reviewer);
        }

        modelVersionMapper.updateById(mv);
        return mv;
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
