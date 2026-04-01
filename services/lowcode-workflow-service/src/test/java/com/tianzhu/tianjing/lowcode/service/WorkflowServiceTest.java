package com.tianzhu.tianjing.lowcode.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianzhu.tianjing.common.exception.BusinessException;
import com.tianzhu.tianjing.common.security.TianjingUserDetails;
import com.tianzhu.tianjing.lowcode.domain.WorkflowDef;
import com.tianzhu.tianjing.lowcode.dto.WorkflowNode;
import com.tianzhu.tianjing.lowcode.dto.WorkflowSaveRequest;
import com.tianzhu.tianjing.lowcode.repository.WorkflowDefMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * 低代码工作流服务单元测试
 *
 * 覆盖：7种节点类型校验、FRAME_IN/ALARM_OUT 强制要求、状态机流转、软删除保护
 * 规范：CLAUDE.md §12.1（低代码编排器后端覆盖率 ≥ 85%）
 * 测试计划：天柱天镜_测试推进计划 §3.1 M1 修复期高优先级
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowService — 节点校验 + 状态机流转")
class WorkflowServiceTest {

    @Mock
    private WorkflowDefMapper workflowDefMapper;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private WorkflowService workflowService;

    private WorkflowDef draftWorkflow;
    private WorkflowDef publishedWorkflow;

    @BeforeEach
    void setUp() {
        // 设置 SecurityContextHolder，使 TianjingUserDetails.current() 返回有效用户
        TianjingUserDetails userDetails = new TianjingUserDetails(
                "designer", 1L, List.of("SCENE_EDITOR"), List.of());
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        draftWorkflow = new WorkflowDef();
        draftWorkflow.setId(1L);
        draftWorkflow.setWorkflowId("WF-SCENE-SINTER-005-ABCD1234");
        draftWorkflow.setSceneId("SCENE-SINTER-005");
        draftWorkflow.setWorkflowName("烧结壁条检测工作流");
        draftWorkflow.setStatus("DRAFT");
        draftWorkflow.setVersion(1);

        publishedWorkflow = new WorkflowDef();
        publishedWorkflow.setId(2L);
        publishedWorkflow.setWorkflowId("WF-SCENE-SINTER-005-EFGH5678");
        publishedWorkflow.setSceneId("SCENE-SINTER-005");
        publishedWorkflow.setWorkflowName("已发布工作流");
        publishedWorkflow.setStatus("PUBLISHED");
        publishedWorkflow.setVersion(3);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── 节点校验 ─────────────────────────────────────────────

    @Test
    @DisplayName("saveWorkflow：合法节点（FRAME_IN + DETECT + ALARM_OUT）→ 保存成功，状态为 DRAFT")
    void saveWorkflow_validNodes_returnsDraftWorkflow() {
        WorkflowSaveRequest request = buildRequest("SCENE-SINTER-005", "壁条检测工作流",
                node("n1", "FRAME_IN"),
                node("n2", "DETECT"),
                node("n3", "ALARM_OUT"));

        WorkflowDef result = workflowService.saveWorkflow(request);

        assertThat(result.getStatus()).isEqualTo("DRAFT");
        assertThat(result.getWorkflowId()).startsWith("WF-");
        assertThat(result.getSceneId()).isEqualTo("SCENE-SINTER-005");
        assertThat(result.getCreatedBy()).isEqualTo("designer");
        verify(workflowDefMapper).insert(any(WorkflowDef.class));
    }

    @Test
    @DisplayName("saveWorkflow：包含全部 7 种节点类型 → 保存成功")
    void saveWorkflow_allSevenNodeTypes_success() {
        WorkflowSaveRequest request = buildRequest("SCENE-STRIP-003", "完整工作流",
                node("n1", "FRAME_IN"),
                node("n2", "PREPROCESS"),
                node("n3", "DETECT"),
                node("n4", "SEGMENT"),
                node("n5", "CLASSIFY"),
                node("n6", "MEASURE"),
                node("n7", "ALARM_OUT"));

        WorkflowDef result = workflowService.saveWorkflow(request);

        assertThat(result.getStatus()).isEqualTo("DRAFT");
        verify(workflowDefMapper).insert(any(WorkflowDef.class));
    }

    @Test
    @DisplayName("saveWorkflow：非法节点类型 → 抛 BusinessException")
    void saveWorkflow_invalidNodeType_throwsException() {
        WorkflowSaveRequest request = buildRequest("SCENE-SINTER-005", "非法工作流",
                node("n1", "FRAME_IN"),
                node("n2", "INVALID_TYPE"),  // 非法类型
                node("n3", "ALARM_OUT"));

        assertThatThrownBy(() -> workflowService.saveWorkflow(request))
                .isInstanceOf(BusinessException.class);
        verify(workflowDefMapper, never()).insert(any(WorkflowDef.class));
    }

    @Test
    @DisplayName("saveWorkflow：缺少 FRAME_IN 节点 → 抛 BusinessException")
    void saveWorkflow_missingFrameIn_throwsException() {
        WorkflowSaveRequest request = buildRequest("SCENE-SINTER-005", "缺少帧输入",
                node("n1", "DETECT"),
                node("n2", "ALARM_OUT"));

        assertThatThrownBy(() -> workflowService.saveWorkflow(request))
                .isInstanceOf(BusinessException.class);
        verify(workflowDefMapper, never()).insert(any(WorkflowDef.class));
    }

    @Test
    @DisplayName("saveWorkflow：缺少 ALARM_OUT 节点 → 抛 BusinessException")
    void saveWorkflow_missingAlarmOut_throwsException() {
        WorkflowSaveRequest request = buildRequest("SCENE-SINTER-005", "缺少告警输出",
                node("n1", "FRAME_IN"),
                node("n2", "DETECT"));

        assertThatThrownBy(() -> workflowService.saveWorkflow(request))
                .isInstanceOf(BusinessException.class);
        verify(workflowDefMapper, never()).insert(any(WorkflowDef.class));
    }

    @Test
    @DisplayName("saveWorkflow：节点列表为空 → 抛 BusinessException")
    void saveWorkflow_emptyNodes_throwsException() {
        WorkflowSaveRequest request = new WorkflowSaveRequest();
        request.setSceneId("SCENE-SINTER-005");
        request.setWorkflowName("空节点工作流");
        request.setNodes(List.of());

        assertThatThrownBy(() -> workflowService.saveWorkflow(request))
                .isInstanceOf(BusinessException.class);
        verify(workflowDefMapper, never()).insert(any(WorkflowDef.class));
    }

    @Test
    @DisplayName("saveWorkflow：多个 FRAME_IN 节点 → 抛 BusinessException（必须有且仅有一个）")
    void saveWorkflow_multipleFrameIn_throwsException() {
        WorkflowSaveRequest request = buildRequest("SCENE-SINTER-005", "多入口工作流",
                node("n1", "FRAME_IN"),
                node("n2", "FRAME_IN"),  // 重复
                node("n3", "ALARM_OUT"));

        assertThatThrownBy(() -> workflowService.saveWorkflow(request))
                .isInstanceOf(BusinessException.class);
    }

    // ── 发布状态机 ────────────────────────────────────────────

    @Test
    @DisplayName("publishWorkflow：DRAFT → PUBLISHED 状态流转成功")
    void publishWorkflow_fromDraft_toPublished() {
        when(workflowDefMapper.selectOne(any())).thenReturn(draftWorkflow);

        WorkflowDef result = workflowService.publishWorkflow("WF-SCENE-SINTER-005-ABCD1234");

        assertThat(result.getStatus()).isEqualTo("PUBLISHED");
        verify(workflowDefMapper).updateById(draftWorkflow);
    }

    @Test
    @DisplayName("publishWorkflow：已是 PUBLISHED 状态 → 抛 BusinessException（不允许重复发布）")
    void publishWorkflow_alreadyPublished_throwsException() {
        when(workflowDefMapper.selectOne(any())).thenReturn(publishedWorkflow);

        assertThatThrownBy(() -> workflowService.publishWorkflow("WF-SCENE-SINTER-005-EFGH5678"))
                .isInstanceOf(BusinessException.class);
        verify(workflowDefMapper, never()).updateById(any(WorkflowDef.class));
    }

    @Test
    @DisplayName("publishWorkflow：工作流不存在 → 抛 BusinessException")
    void publishWorkflow_notFound_throwsException() {
        when(workflowDefMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> workflowService.publishWorkflow("WF-NONEXIST-0000"))
                .isInstanceOf(BusinessException.class);
    }

    // ── 删除保护 ─────────────────────────────────────────────

    @Test
    @DisplayName("deleteWorkflow：DRAFT 状态允许删除，deleteById 被调用")
    void deleteWorkflow_draft_deletesSuccessfully() {
        when(workflowDefMapper.selectOne(any())).thenReturn(draftWorkflow);

        workflowService.deleteWorkflow("WF-SCENE-SINTER-005-ABCD1234");

        verify(workflowDefMapper).deleteById(draftWorkflow.getId());
    }

    @Test
    @DisplayName("deleteWorkflow：PUBLISHED 状态禁止删除 → 抛 BusinessException")
    void deleteWorkflow_published_throwsException() {
        when(workflowDefMapper.selectOne(any())).thenReturn(publishedWorkflow);

        assertThatThrownBy(() -> workflowService.deleteWorkflow("WF-SCENE-SINTER-005-EFGH5678"))
                .isInstanceOf(BusinessException.class);
        verify(workflowDefMapper, never()).deleteById(anyLong());
    }

    // ── 辅助方法 ─────────────────────────────────────────────

    private WorkflowSaveRequest buildRequest(String sceneId, String name, WorkflowNode... nodes) {
        WorkflowSaveRequest request = new WorkflowSaveRequest();
        request.setSceneId(sceneId);
        request.setWorkflowName(name);
        request.setNodes(List.of(nodes));
        return request;
    }

    private WorkflowNode node(String id, String type) {
        WorkflowNode node = new WorkflowNode();
        node.setId(id);
        node.setType(type);
        return node;
    }
}
