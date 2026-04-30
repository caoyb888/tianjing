package com.tianzhu.tianjing.algomodel.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tianzhu.tianjing.algomodel.domain.ModelVersion;
import com.tianzhu.tianjing.algomodel.dto.ModelApproveRequest;
import com.tianzhu.tianjing.algomodel.dto.ModelRegisterRequest;
import com.tianzhu.tianjing.common.exception.ErrorCode;
import com.tianzhu.tianjing.algomodel.repository.ModelVersionMapper;
import com.tianzhu.tianjing.common.exception.BusinessException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 模型版本服务单元测试
 *
 * 覆盖：状态机流转、四眼原则、蓝绿切换
 * 规范：CLAUDE.md §12.1（Service 层覆盖率 ≥ 80%）
 * 测试计划：天柱天镜_测试推进计划 §3.1 M1 修复期高优先级
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ModelVersionService — 状态机 + 四眼原则")
class ModelVersionServiceTest {

    @Mock
    private ModelVersionMapper modelVersionMapper;

    @InjectMocks
    private ModelVersionService modelVersionService;

    private ModelVersion stagingVersion;
    private ModelVersion sandboxValidatingVersion;
    private ModelVersion reviewingVersion;
    private ModelVersion productionVersion;

    @BeforeEach
    void setUp() {
        // 初始化 MyBatis-Plus lambda cache（纯 Mockito 单元测试无 Spring Context，需手动注册）
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), ModelVersion.class);

        stagingVersion = new ModelVersion();
        stagingVersion.setVersionId("MV-STAGING-001");
        stagingVersion.setPluginId("PLUGIN-001");
        stagingVersion.setStatus("STAGING");
        stagingVersion.setCreatedBy("alice");

        sandboxValidatingVersion = new ModelVersion();
        sandboxValidatingVersion.setVersionId("MV-SBX-001");
        sandboxValidatingVersion.setPluginId("PLUGIN-001");
        sandboxValidatingVersion.setStatus("SANDBOX_VALIDATING");
        sandboxValidatingVersion.setCreatedBy("alice");

        reviewingVersion = new ModelVersion();
        reviewingVersion.setVersionId("MV-REVIEW-001");
        reviewingVersion.setPluginId("PLUGIN-001");
        reviewingVersion.setStatus("REVIEWING");
        reviewingVersion.setCreatedBy("alice");

        productionVersion = new ModelVersion();
        productionVersion.setVersionId("MV-PROD-001");
        productionVersion.setPluginId("PLUGIN-001");
        productionVersion.setStatus("PRODUCTION");
        productionVersion.setCreatedBy("alice");
    }

    // ── 注册 ──────────────────────────────────────────────────

    @Test
    @DisplayName("registerVersion：注册后状态为 STAGING，提交人字段正确赋值")
    void registerVersion_setsStatusAndSubmitter() {
        ModelRegisterRequest req = new ModelRegisterRequest(
                "PLUGIN-001", "1.0.0", "run-abc", "minio://models/1.0.0", null);

        ModelVersion result = modelVersionService.registerVersion(req, "alice");

        assertThat(result.getStatus()).isEqualTo("STAGING");
        assertThat(result.getPluginId()).isEqualTo("PLUGIN-001");
        assertThat(result.getCreatedBy()).isEqualTo("alice");
        assertThat(result.getVersionId()).startsWith("MV-");
        verify(modelVersionMapper).insert(any(ModelVersion.class));
    }

    // ── 状态机：合法流转 ──────────────────────────────────────

    @Test
    @DisplayName("submitReview：STAGING → REVIEWING 流转成功（不检查 sandbox_hours）")
    void submitReview_fromStaging_toReviewing() {
        ReflectionTestUtils.setField(modelVersionService, "minSandboxHours", 48);
        when(modelVersionMapper.selectOne(any())).thenReturn(stagingVersion);

        ModelVersion result = modelVersionService.submitReview("MV-STAGING-001", "alice");

        assertThat(result.getStatus()).isEqualTo("REVIEWING");
        verify(modelVersionMapper).updateById((ModelVersion) stagingVersion);
    }

    @Test
    @DisplayName("submitReview：SANDBOX_VALIDATING → REVIEWING 流转成功（min-hours=0，门禁关闭）")
    void submitReview_fromSandboxValidating_toReviewing() {
        // 默认 @InjectMocks 注入的 minSandboxHours=0（Sprint 3 测试模式）
        ReflectionTestUtils.setField(modelVersionService, "minSandboxHours", 0);
        sandboxValidatingVersion.setSandboxHours(0);
        when(modelVersionMapper.selectOne(any())).thenReturn(sandboxValidatingVersion);

        ModelVersion result = modelVersionService.submitReview("MV-SBX-001", "alice");

        assertThat(result.getStatus()).isEqualTo("REVIEWING");
        verify(modelVersionMapper).updateById((ModelVersion) sandboxValidatingVersion);
    }

    @Test
    @DisplayName("submitReview：SANDBOX_VALIDATING 时 sandbox_hours 不足 → SANDBOX_HOURS_INSUFFICIENT")
    void submitReview_fromSandboxValidating_hoursInsufficient_throws() {
        ReflectionTestUtils.setField(modelVersionService, "minSandboxHours", 48);
        sandboxValidatingVersion.setSandboxHours(10); // 仅 10h，未达到 48h
        when(modelVersionMapper.selectOne(any())).thenReturn(sandboxValidatingVersion);

        assertThatThrownBy(() -> modelVersionService.submitReview("MV-SBX-001", "alice"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SANDBOX_HOURS_INSUFFICIENT));
        verify(modelVersionMapper, never()).updateById(any(ModelVersion.class));
    }

    @Test
    @DisplayName("submitReview：SANDBOX_VALIDATING sandbox_hours 达到 min-hours 阈值 → 通过")
    void submitReview_fromSandboxValidating_hoursMetThreshold_succeeds() {
        ReflectionTestUtils.setField(modelVersionService, "minSandboxHours", 48);
        sandboxValidatingVersion.setSandboxHours(48);
        when(modelVersionMapper.selectOne(any())).thenReturn(sandboxValidatingVersion);

        ModelVersion result = modelVersionService.submitReview("MV-SBX-001", "alice");

        assertThat(result.getStatus()).isEqualTo("REVIEWING");
        verify(modelVersionMapper).updateById((ModelVersion) sandboxValidatingVersion);
    }

    @Test
    @DisplayName("submitReview：STAGING 状态不受 sandbox_hours 门禁约束，直接流转 REVIEWING")
    void submitReview_fromStaging_ignoreSandboxHoursGate() {
        ReflectionTestUtils.setField(modelVersionService, "minSandboxHours", 48);
        stagingVersion.setSandboxHours(0); // sandbox_hours=0，但 STAGING 不检查
        when(modelVersionMapper.selectOne(any())).thenReturn(stagingVersion);

        ModelVersion result = modelVersionService.submitReview("MV-STAGING-001", "alice");

        assertThat(result.getStatus()).isEqualTo("REVIEWING");
    }

    // ── 状态机：非法跳步 ──────────────────────────────────────

    @Test
    @DisplayName("submitReview：REVIEWING 状态不允许再次提交审核 → 抛 BusinessException")
    void submitReview_fromReviewing_throwsException() {
        when(modelVersionMapper.selectOne(any())).thenReturn(reviewingVersion);

        assertThatThrownBy(() -> modelVersionService.submitReview("MV-REVIEW-001", "alice"))
                .isInstanceOf(BusinessException.class);
        verify(modelVersionMapper, never()).updateById(any(ModelVersion.class));
    }

    @Test
    @DisplayName("submitReview：PRODUCTION 状态不允许提交审核 → 抛 BusinessException")
    void submitReview_fromProduction_throwsException() {
        when(modelVersionMapper.selectOne(any())).thenReturn(productionVersion);

        assertThatThrownBy(() -> modelVersionService.submitReview("MV-PROD-001", "alice"))
                .isInstanceOf(BusinessException.class);
    }

    // ── 四眼原则 ─────────────────────────────────────────────

    @Test
    @DisplayName("approve：审核人与提交人相同 → 四眼原则违规，抛 BusinessException（错误码 4004）")
    void approve_sameUserAsSubmitter_throwsFourEyesViolation() {
        when(modelVersionMapper.selectOne(any())).thenReturn(reviewingVersion);
        ModelApproveRequest approveReq = new ModelApproveRequest(true, "LGTM");

        // 审核人 alice == 提交人 alice → 违反四眼原则
        assertThatThrownBy(() -> modelVersionService.approve("MV-REVIEW-001", approveReq, "alice"))
                .isInstanceOf(BusinessException.class);
        verify(modelVersionMapper, never()).updateById(any(ModelVersion.class));
    }

    @Test
    @DisplayName("approve：不同 MODEL_REVIEWER 审批通过 → 状态变更为 PRODUCTION")
    void approve_differentReviewer_approved_toProduction() {
        when(modelVersionMapper.selectOne(any())).thenReturn(reviewingVersion);
        ModelApproveRequest approveReq = new ModelApproveRequest(true, "通过");

        // 审核人 bob ≠ 提交人 alice → 满足四眼原则
        ModelVersion result = modelVersionService.approve("MV-REVIEW-001", approveReq, "bob");

        assertThat(result.getStatus()).isEqualTo("PRODUCTION");
        assertThat(result.getApprovedBy()).isEqualTo("bob");
    }

    @Test
    @DisplayName("approve：不同 MODEL_REVIEWER 审批拒绝 → 状态回退为 STAGING")
    void approve_differentReviewer_rejected_backToStaging() {
        when(modelVersionMapper.selectOne(any())).thenReturn(reviewingVersion);
        ModelApproveRequest rejectReq = new ModelApproveRequest(false, "精度不达标");

        ModelVersion result = modelVersionService.approve("MV-REVIEW-001", rejectReq, "bob");

        assertThat(result.getStatus()).isEqualTo("STAGING");
        assertThat(result.getApprovedBy()).isEqualTo("bob");
    }

    // ── 蓝绿切换 ─────────────────────────────────────────────

    @Test
    @DisplayName("approve：审批通过时，旧 PRODUCTION 版本自动 DEPRECATED（蓝绿切换）")
    void approve_approved_deprecatesOldProductionVersion() {
        when(modelVersionMapper.selectOne(any())).thenReturn(reviewingVersion);
        ModelApproveRequest approveReq = new ModelApproveRequest(true, "上线");

        modelVersionService.approve("MV-REVIEW-001", approveReq, "bob");

        // 验证对旧 PRODUCTION 版本执行了 DEPRECATED 更新（LambdaUpdateWrapper）
        verify(modelVersionMapper).update(any());
        verify(modelVersionMapper).updateById((ModelVersion) reviewingVersion);
    }

    // ── deprecate ────────────────────────────────────────────

    @Test
    @DisplayName("deprecate：版本状态变更为 DEPRECATED")
    void deprecate_setsStatusToDeprecated() {
        when(modelVersionMapper.selectOne(any())).thenReturn(productionVersion);

        modelVersionService.deprecate("MV-PROD-001", "admin");

        assertThat(productionVersion.getStatus()).isEqualTo("DEPRECATED");
        verify(modelVersionMapper).updateById((ModelVersion) productionVersion);
    }

    // ── 非法操作：非 REVIEWING 状态审核 ─────────────────────

    @Test
    @DisplayName("approve：非 REVIEWING 状态执行审核 → 抛 BusinessException")
    void approve_notInReviewingStatus_throwsException() {
        when(modelVersionMapper.selectOne(any())).thenReturn(stagingVersion);
        ModelApproveRequest req = new ModelApproveRequest(true, "");

        assertThatThrownBy(() -> modelVersionService.approve("MV-STAGING-001", req, "bob"))
                .isInstanceOf(BusinessException.class);
    }
}
