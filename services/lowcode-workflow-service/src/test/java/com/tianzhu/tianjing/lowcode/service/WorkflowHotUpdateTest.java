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
import static org.mockito.Mockito.*;

/**
 * 工作流热更新单元测试
 *
 * 覆盖：乐观锁版本冲突检测、版本匹配时更新成功、version=null 时跳过版本校验
 * 规范：CLAUDE.md §7.1（乐观锁版本号）、§12.1（低代码编排器覆盖率 ≥ 85%）
 * 测试计划：天柱天镜_测试推进计划 §3.1 M1 修复期高优先级
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowService — 乐观锁热更新")
class WorkflowHotUpdateTest {

    @Mock
    private WorkflowDefMapper workflowDefMapper;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private WorkflowService workflowService;

    private WorkflowDef existingWorkflow;

    @BeforeEach
    void setUp() {
        // 设置 SecurityContextHolder（updateWorkflow 内部不调用 current()，但保持一致性）
        TianjingUserDetails userDetails = new TianjingUserDetails(
                "editor", 2L, List.of("SCENE_EDITOR"), List.of());
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);

        existingWorkflow = new WorkflowDef();
        existingWorkflow.setId(10L);
        existingWorkflow.setWorkflowId("WF-SCENE-STEEL-001-WXYZ9876");
        existingWorkflow.setSceneId("SCENE-STEEL-001");
        existingWorkflow.setWorkflowName("钢材表面缺陷工作流");
        existingWorkflow.setStatus("DRAFT");
        existingWorkflow.setWorkflowJson("{\"nodes\":[],\"edges\":[]}");
        existingWorkflow.setVersion(2);  // 当前数据库版本为 2
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ── 版本匹配 ─────────────────────────────────────────────

    @Test
    @DisplayName("updateWorkflow：请求版本与数据库版本一致 → 更新成功，updateById 被调用")
    void updateWorkflow_matchingVersion_updatesSuccessfully() {
        when(workflowDefMapper.selectOne(any())).thenReturn(existingWorkflow);
        WorkflowSaveRequest request = buildUpdateRequest(2);  // 版本匹配

        WorkflowDef result = workflowService.updateWorkflow(
                "WF-SCENE-STEEL-001-WXYZ9876", request);

        assertThat(result.getWorkflowName()).isEqualTo("更新后的工作流名称");
        verify(workflowDefMapper).updateById(existingWorkflow);
    }

    @Test
    @DisplayName("updateWorkflow：请求版本为 null → 跳过版本校验，更新成功")
    void updateWorkflow_nullVersion_skipsVersionCheckAndUpdates() {
        when(workflowDefMapper.selectOne(any())).thenReturn(existingWorkflow);
        WorkflowSaveRequest request = buildUpdateRequest(null);  // 不传版本号

        WorkflowDef result = workflowService.updateWorkflow(
                "WF-SCENE-STEEL-001-WXYZ9876", request);

        assertThat(result).isNotNull();
        verify(workflowDefMapper).updateById(existingWorkflow);
    }

    // ── 版本冲突 ─────────────────────────────────────────────

    @Test
    @DisplayName("updateWorkflow：请求版本与数据库版本不一致 → 抛 BusinessException（乐观锁冲突）")
    void updateWorkflow_mismatchedVersion_throwsConflictException() {
        when(workflowDefMapper.selectOne(any())).thenReturn(existingWorkflow);
        WorkflowSaveRequest request = buildUpdateRequest(1);  // 过期版本（当前为 2）

        assertThatThrownBy(() -> workflowService.updateWorkflow(
                "WF-SCENE-STEEL-001-WXYZ9876", request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("乐观锁版本冲突");
        verify(workflowDefMapper, never()).updateById(any(WorkflowDef.class));
    }

    @Test
    @DisplayName("updateWorkflow：并发场景 — 两个请求同版本，第二个触发冲突")
    void updateWorkflow_concurrentUpdate_secondRequestConflicts() {
        // 模拟第一次更新后版本已变更为 3
        WorkflowDef versionUpdated = new WorkflowDef();
        versionUpdated.setId(10L);
        versionUpdated.setWorkflowId("WF-SCENE-STEEL-001-WXYZ9876");
        versionUpdated.setSceneId("SCENE-STEEL-001");
        versionUpdated.setWorkflowName("已被第一个请求修改的工作流");
        versionUpdated.setStatus("DRAFT");
        versionUpdated.setVersion(3);  // 数据库版本已更新为 3
        when(workflowDefMapper.selectOne(any())).thenReturn(versionUpdated);

        // 第二个请求携带旧版本号 2（已过期）
        WorkflowSaveRequest staleRequest = buildUpdateRequest(2);

        assertThatThrownBy(() -> workflowService.updateWorkflow(
                "WF-SCENE-STEEL-001-WXYZ9876", staleRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("乐观锁版本冲突");
        verify(workflowDefMapper, never()).updateById(any(WorkflowDef.class));
    }

    @Test
    @DisplayName("updateWorkflow：工作流不存在 → 抛 BusinessException")
    void updateWorkflow_notFound_throwsException() {
        when(workflowDefMapper.selectOne(any())).thenReturn(null);
        WorkflowSaveRequest request = buildUpdateRequest(1);

        assertThatThrownBy(() -> workflowService.updateWorkflow("WF-NONEXIST-0000", request))
                .isInstanceOf(BusinessException.class);
        verify(workflowDefMapper, never()).updateById(any(WorkflowDef.class));
    }

    @Test
    @DisplayName("updateWorkflow：更新时节点校验仍生效，非法节点类型 → 抛 BusinessException")
    void updateWorkflow_invalidNodeType_throwsExceptionBeforeUpdate() {
        when(workflowDefMapper.selectOne(any())).thenReturn(existingWorkflow);

        WorkflowSaveRequest request = new WorkflowSaveRequest();
        request.setSceneId("SCENE-STEEL-001");
        request.setWorkflowName("更新后的工作流名称");
        request.setVersion(2);
        request.setNodes(List.of(
                node("n1", "FRAME_IN"),
                node("n2", "UNKNOWN_NODE"),  // 非法
                node("n3", "ALARM_OUT")
        ));

        assertThatThrownBy(() -> workflowService.updateWorkflow(
                "WF-SCENE-STEEL-001-WXYZ9876", request))
                .isInstanceOf(BusinessException.class);
        verify(workflowDefMapper, never()).updateById(any(WorkflowDef.class));
    }

    // ── 辅助方法 ─────────────────────────────────────────────

    private WorkflowSaveRequest buildUpdateRequest(Integer version) {
        WorkflowSaveRequest request = new WorkflowSaveRequest();
        request.setSceneId("SCENE-STEEL-001");
        request.setWorkflowName("更新后的工作流名称");
        request.setVersion(version);
        request.setNodes(List.of(
                node("n1", "FRAME_IN"),
                node("n2", "DETECT"),
                node("n3", "ALARM_OUT")
        ));
        return request;
    }

    private WorkflowNode node(String id, String type) {
        WorkflowNode node = new WorkflowNode();
        node.setId(id);
        node.setType(type);
        return node;
    }
}
