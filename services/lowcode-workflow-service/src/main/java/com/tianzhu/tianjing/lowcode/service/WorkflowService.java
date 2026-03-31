package com.tianzhu.tianjing.lowcode.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianzhu.tianjing.common.exception.BusinessException;
import com.tianzhu.tianjing.common.exception.ErrorCode;
import com.tianzhu.tianjing.common.response.PageResult;
import com.tianzhu.tianjing.common.security.TianjingUserDetails;
import com.tianzhu.tianjing.lowcode.domain.WorkflowDef;
import com.tianzhu.tianjing.lowcode.dto.WorkflowNode;
import com.tianzhu.tianjing.lowcode.dto.WorkflowSaveRequest;
import com.tianzhu.tianjing.lowcode.repository.WorkflowDefMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 低代码工作流服务
 *
 * 规范：CLAUDE.md §4.2 lowcode-workflow-service
 *   - 生成场景配置 JSON、下发至路由服务
 *   - 禁止直接操作推理进程
 *   - 禁止绕过配置中心
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowService {

    /**
     * 合法节点类型（7 种，规范：CLAUDE.md §4.2）
     */
    private static final Set<String> VALID_NODE_TYPES = Set.of(
            "FRAME_IN", "PREPROCESS", "DETECT", "SEGMENT", "CLASSIFY", "MEASURE", "ALARM_OUT"
    );

    private final WorkflowDefMapper workflowDefMapper;
    private final ObjectMapper objectMapper;

    /**
     * 保存工作流草稿（DRAFT 状态）
     */
    @Transactional
    public WorkflowDef saveWorkflow(WorkflowSaveRequest request) {
        validateNodes(request.getNodes());

        String workflowJson = buildWorkflowJson(request);
        String operator = TianjingUserDetails.current().getUsername();

        WorkflowDef workflow = new WorkflowDef();
        workflow.setWorkflowId("WF-" + request.getSceneId() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        workflow.setSceneId(request.getSceneId());
        workflow.setWorkflowName(request.getWorkflowName());
        workflow.setStatus("DRAFT");
        workflow.setWorkflowJson(workflowJson);
        workflow.setCreatedBy(operator);

        workflowDefMapper.insert(workflow);
        log.info("工作流草稿已保存", "workflowId", workflow.getWorkflowId(), "sceneId", request.getSceneId());
        return workflow;
    }

    /**
     * 更新工作流（乐观锁保护）
     */
    @Transactional
    public WorkflowDef updateWorkflow(String workflowId, WorkflowSaveRequest request) {
        WorkflowDef existing = getWorkflowOrThrow(workflowId);

        if (request.getVersion() != null && !request.getVersion().equals(existing.getVersion())) {
            throw BusinessException.conflict("工作流已被他人修改，请刷新后重试");
        }

        validateNodes(request.getNodes());
        String workflowJson = buildWorkflowJson(request);

        existing.setWorkflowName(request.getWorkflowName());
        existing.setWorkflowJson(workflowJson);
        workflowDefMapper.updateById(existing);
        return existing;
    }

    /**
     * 发布工作流（DRAFT → PUBLISHED）
     * 发布后同步至配置中心（Nacos），route-dispatch-service 热加载
     */
    @Transactional
    public WorkflowDef publishWorkflow(String workflowId) {
        WorkflowDef workflow = getWorkflowOrThrow(workflowId);

        if (!"DRAFT".equals(workflow.getStatus())) {
            throw BusinessException.of(ErrorCode.RESOURCE_STATE_FORBIDDEN, "只有 DRAFT 状态的工作流可以发布");
        }

        workflow.setStatus("PUBLISHED");
        workflowDefMapper.updateById(workflow);

        log.info("工作流已发布", "workflowId", workflowId, "sceneId", workflow.getSceneId());
        return workflow;
    }

    /**
     * 查询场景下所有工作流
     */
    public PageResult<WorkflowDef> listByScene(String sceneId, int page, int size) {
        LambdaQueryWrapper<WorkflowDef> wrapper = new LambdaQueryWrapper<WorkflowDef>()
                .eq(sceneId != null, WorkflowDef::getSceneId, sceneId)
                .orderByDesc(WorkflowDef::getUpdatedAt);

        long total = workflowDefMapper.selectCount(wrapper);
        List<WorkflowDef> items = workflowDefMapper.selectList(
                wrapper.last("LIMIT " + size + " OFFSET " + ((page - 1) * size))
        );
        return PageResult.of(total, page, size, items);
    }

    /**
     * 获取工作流详情
     */
    public WorkflowDef getWorkflow(String workflowId) {
        return getWorkflowOrThrow(workflowId);
    }

    /**
     * 删除工作流（软删除，只允许删除 DRAFT 状态）
     */
    @Transactional
    public void deleteWorkflow(String workflowId) {
        WorkflowDef workflow = getWorkflowOrThrow(workflowId);
        if ("PUBLISHED".equals(workflow.getStatus())) {
            throw BusinessException.of(ErrorCode.RESOURCE_STATE_FORBIDDEN, "已发布的工作流不能直接删除，请先归档");
        }
        workflowDefMapper.deleteById(workflow.getId());
    }

    // ===== 私有方法 =====

    private void validateNodes(List<WorkflowNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "工作流节点不能为空");
        }

        for (WorkflowNode node : nodes) {
            if (!VALID_NODE_TYPES.contains(node.getType())) {
                throw BusinessException.of(ErrorCode.PARAM_INVALID,
                        "不合法的节点类型: " + node.getType() + "，允许类型: " + VALID_NODE_TYPES);
            }
        }

        // 校验：必须有且仅有一个 FRAME_IN 节点和一个 ALARM_OUT 节点
        long frameInCount = nodes.stream().filter(n -> "FRAME_IN".equals(n.getType())).count();
        long alarmOutCount = nodes.stream().filter(n -> "ALARM_OUT".equals(n.getType())).count();

        if (frameInCount != 1) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "工作流必须有且仅有一个 FRAME_IN 节点");
        }
        if (alarmOutCount != 1) {
            throw BusinessException.of(ErrorCode.PARAM_INVALID, "工作流必须有且仅有一个 ALARM_OUT 节点");
        }
    }

    private String buildWorkflowJson(WorkflowSaveRequest request) {
        try {
            Map<String, Object> workflowMap = Map.of(
                    "nodes", request.getNodes(),
                    "edges", request.getEdges() != null ? request.getEdges() : List.of(),
                    "metadata", Map.of(
                            "scene_id", request.getSceneId(),
                            "workflow_name", request.getWorkflowName(),
                            "node_count", request.getNodes().size()
                    )
            );
            return objectMapper.writeValueAsString(workflowMap);
        } catch (Exception e) {
            throw BusinessException.of(ErrorCode.INTERNAL_ERROR, "工作流 JSON 序列化失败: " + e.getMessage());
        }
    }

    private WorkflowDef getWorkflowOrThrow(String workflowId) {
        WorkflowDef workflow = workflowDefMapper.selectOne(
                new LambdaQueryWrapper<WorkflowDef>().eq(WorkflowDef::getWorkflowId, workflowId)
        );
        if (workflow == null) {
            throw BusinessException.notFound(ErrorCode.SCENE_NOT_FOUND);
        }
        return workflow;
    }
}
