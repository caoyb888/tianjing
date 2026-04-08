package com.tianzhu.tianjing.replay.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianzhu.tianjing.common.exception.BusinessException;
import com.tianzhu.tianjing.common.exception.ErrorCode;
import com.tianzhu.tianjing.common.response.PageResult;
import com.tianzhu.tianjing.replay.domain.SimulationTask;
import com.tianzhu.tianjing.replay.domain.entity.SimulationFrameReviewEntity;
import com.tianzhu.tianjing.replay.dto.*;
import com.tianzhu.tianjing.replay.repository.SimulationFrameReviewMapper;
import com.tianzhu.tianjing.replay.repository.SimulationTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * 标注审核服务
 * 规范：标注审核工具开发计划 V1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnotationReviewService {

    private final SimulationFrameReviewMapper reviewMapper;
    private final SimulationTaskMapper taskMapper;
    private final ObjectMapper objectMapper;

    // ============================================================================
    // 1. 初始化审核记录
    // ============================================================================

    /**
     * 初始化审核记录：从 simulation_task.result_json 解析帧数据，批量创建审核记录
     * 幂等：若已存在记录则跳过，不覆盖
     *
     * @param taskId 任务ID
     * @return 初始化结果统计
     */
    @Transactional(rollbackFor = Exception.class)
    public InitReviewResult initReview(String taskId) {
        SimulationTask task = taskMapper.selectOne(
                Wrappers.<SimulationTask>lambdaQuery()
                        .eq(SimulationTask::getTaskId, taskId)
                        .eq(SimulationTask::getIsDeleted, false));

        if (task == null) {
            throw new BusinessException(ErrorCode.SIMULATION_TASK_NOT_FOUND, "仿真任务不存在: " + taskId);
        }

        if (!"COMPLETED".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.RESOURCE_STATE_FORBIDDEN,
                    "任务未完成，无法初始化审核: status=" + task.getStatus());
        }

        // 解析 result_json
        List<FrameInfo> frames = parseResultJsonFrames(task.getResultJson());
        if (frames.isEmpty()) {
            throw new BusinessException(ErrorCode.SCENE_ENABLE_PRECONDITION,
                    "任务 result_json 为空或无帧数据");
        }

        // 查询已存在的记录数
        long existedCount = reviewMapper.selectCount(
                Wrappers.<SimulationFrameReviewEntity>lambdaQuery()
                        .eq(SimulationFrameReviewEntity::getTaskId, taskId));

        int created = 0;
        int skipped = 0;

        for (int i = 0; i < frames.size(); i++) {
            FrameInfo frame = frames.get(i);

            // 幂等检查：已存在则跳过
            SimulationFrameReviewEntity existing = reviewMapper.selectByTaskIdAndFrameId(taskId, frame.frameId);
            if (existing != null) {
                skipped++;
                continue;
            }

            // 创建新记录
            SimulationFrameReviewEntity entity = new SimulationFrameReviewEntity();
            entity.setTaskId(taskId);
            entity.setFrameId(frame.frameId);
            entity.setFrameUrl(frame.frameUrl);
            entity.setFrameIndex(i);
            entity.setOriginalDetectionsJson(toJson(frame.detections));
            entity.setCorrectedDetectionsJson(null);
            entity.setReviewStatus("PENDING");
            entity.setIsModified(false);
            entity.setVersion(0);
            entity.setIsDeleted(false);

            reviewMapper.insert(entity);
            created++;
        }

        // 更新任务审核进度
        updateTaskReviewProgress(taskId);

        log.info("初始化审核完成 task_id={} total={} existed={} created={} skipped={}",
                taskId, frames.size(), existedCount, created, skipped);

        return new InitReviewResult(frames.size(), (int) existedCount, created);
    }

    // ============================================================================
    // 2. 审核统计
    // ============================================================================

    /**
     * 获取审核进度统计
     *
     * @param taskId 任务ID
     * @return 统计信息
     */
    public ReviewStatsDTO getStats(String taskId) {
        // 验证任务存在
        SimulationTask task = taskMapper.selectOne(
                Wrappers.<SimulationTask>lambdaQuery()
                        .eq(SimulationTask::getTaskId, taskId)
                        .eq(SimulationTask::getIsDeleted, false));
        if (task == null) {
            throw new BusinessException(ErrorCode.SIMULATION_TASK_NOT_FOUND, "仿真任务不存在: " + taskId);
        }

        // 查询各状态数量
        long total = reviewMapper.selectCount(
                Wrappers.<SimulationFrameReviewEntity>lambdaQuery()
                        .eq(SimulationFrameReviewEntity::getTaskId, taskId));

        long pending = reviewMapper.countByTaskIdAndStatus(taskId, "PENDING");
        long approved = reviewMapper.countByTaskIdAndStatus(taskId, "APPROVED");
        long rejected = reviewMapper.countByTaskIdAndStatus(taskId, "REJECTED");
        long skipped = reviewMapper.countByTaskIdAndStatus(taskId, "SKIPPED");

        // 已审核 = APPROVED + REJECTED（SKIPPED 不算已审核）
        long reviewed = approved + rejected;
        int progressPercent = total > 0 ? (int) (reviewed * 100 / total) : 0;

        return new ReviewStatsDTO(
                (int) total,
                (int) reviewed,
                (int) pending,
                (int) approved,
                (int) rejected,
                (int) skipped,
                progressPercent
        );
    }

    // ============================================================================
    // 3. 分页获取帧列表
    // ============================================================================

    /**
     * 分页获取帧列表（带审核状态，用于缩略图导航）
     *
     * @param taskId 任务ID
     * @param page   页码（1-based）
     * @param size   每页大小
     * @param status 状态筛选（可选：PENDING/APPROVED/REJECTED/SKIPPED）
     * @return 分页结果
     */
    public PageResult<FrameListItemDTO> listFrames(String taskId, int page, int size, String status) {
        // 验证任务存在
        SimulationTask task = taskMapper.selectOne(
                Wrappers.<SimulationTask>lambdaQuery()
                        .eq(SimulationTask::getTaskId, taskId)
                        .eq(SimulationTask::getIsDeleted, false));
        if (task == null) {
            throw new BusinessException(ErrorCode.SIMULATION_TASK_NOT_FOUND, "仿真任务不存在: " + taskId);
        }

        // 构建查询条件
        LambdaQueryWrapper<SimulationFrameReviewEntity> wrapper = Wrappers.lambdaQuery();
        wrapper.eq(SimulationFrameReviewEntity::getTaskId, taskId);
        if (status != null && !status.isBlank()) {
            wrapper.eq(SimulationFrameReviewEntity::getReviewStatus, status);
        }
        wrapper.orderByAsc(SimulationFrameReviewEntity::getFrameIndex);

        // 查询总数
        long total = reviewMapper.selectCount(wrapper);

        // 分页查询
        int offset = (page - 1) * size;
        wrapper.last("LIMIT " + size + " OFFSET " + offset);
        List<SimulationFrameReviewEntity> entities = reviewMapper.selectList(wrapper);

        // 转换为 DTO
        List<FrameListItemDTO> items = entities.stream().map(e -> {
            int detectionCount = parseDetections(e.getOriginalDetectionsJson()).size();
            Integer correctedCount = e.getCorrectedDetectionsJson() != null
                    ? parseDetections(e.getCorrectedDetectionsJson()).size()
                    : null;

            return new FrameListItemDTO(
                    e.getFrameId(),
                    e.getFrameUrl(),
                    e.getFrameIndex(),
                    e.getReviewStatus(),
                    e.getIsModified(),
                    detectionCount,
                    correctedCount
            );
        }).toList();

        return PageResult.of(total, page, size, items);
    }

    // ============================================================================
    // 4. 获取单帧详情
    // ============================================================================

    /**
     * 获取单帧详情（含原始标注 + 校正标注 + 图像URL）
     *
     * @param taskId  任务ID
     * @param frameId 帧ID
     * @return 帧详情
     */
    public FrameDetailDTO getFrame(String taskId, String frameId) {
        SimulationFrameReviewEntity entity = reviewMapper.selectByTaskIdAndFrameId(taskId, frameId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.SIMULATION_TASK_NOT_FOUND,
                    "帧审核记录不存在: task_id=" + taskId + ", frame_id=" + frameId);
        }

        List<DetectionDTO> originalDetections = parseDetections(entity.getOriginalDetectionsJson());
        List<DetectionDTO> correctedDetections = entity.getCorrectedDetectionsJson() != null
                ? parseDetections(entity.getCorrectedDetectionsJson())
                : null;

        return new FrameDetailDTO(
                entity.getFrameId(),
                entity.getFrameUrl(),
                entity.getFrameIndex(),
                entity.getReviewStatus(),
                entity.getIsModified(),
                entity.getReviewedBy(),
                entity.getReviewedAt(),
                originalDetections,
                correctedDetections
        );
    }

    // ============================================================================
    // 5. 保存单帧审核结果
    // ============================================================================

    /**
     * 保存单帧校正结果（标注框 + 审核状态）
     *
     * @param taskId    任务ID
     * @param frameId   帧ID
     * @param request   保存请求
     * @param username  当前用户
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveFrame(String taskId, String frameId, SaveFrameRequest request, String username) {
        SimulationFrameReviewEntity entity = reviewMapper.selectByTaskIdAndFrameId(taskId, frameId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.SIMULATION_TASK_NOT_FOUND,
                    "帧审核记录不存在: task_id=" + taskId + ", frame_id=" + frameId);
        }

        // 解析 corrected_detections
        String correctedJson = null;
        boolean isModified = false;

        if (request.correctedDetections() != null) {
            // 有校正内容（可能是空数组[]或有内容的数组）
            correctedJson = toJson(request.correctedDetections());
            // 与原始结果比较判断是否修改
            String originalJson = entity.getOriginalDetectionsJson();
            isModified = !Objects.equals(correctedJson, originalJson);
        }
        // correctedDetections == null 表示未修改，使用原始结果

        entity.setReviewStatus(request.reviewStatus());
        entity.setCorrectedDetectionsJson(correctedJson);
        entity.setIsModified(isModified);
        entity.setReviewedBy(username);
        entity.setReviewedAt(OffsetDateTime.now());

        int updated = reviewMapper.updateById(entity);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.OPTIMISTIC_LOCK_CONFLICT,
                    "该帧审核记录已被其他用户修改，请刷新后重试");
        }

        // 更新任务审核进度
        updateTaskReviewProgress(taskId);

        log.info("保存帧审核结果 task_id={} frame_id={} status={} is_modified={} by={}",
                taskId, frameId, request.reviewStatus(), isModified, username);
    }

    // ============================================================================
    // 6. 批量通过
    // ============================================================================

    /**
     * 批量通过审核
     *
     * @param taskId   任务ID
     * @param request  批量审核请求
     * @param username 当前用户
     * @return 处理的帧数
     */
    @Transactional(rollbackFor = Exception.class)
    public int bulkApprove(String taskId, BulkApproveRequest request, String username) {
        // 验证任务存在
        SimulationTask task = taskMapper.selectOne(
                Wrappers.<SimulationTask>lambdaQuery()
                        .eq(SimulationTask::getTaskId, taskId)
                        .eq(SimulationTask::getIsDeleted, false));
        if (task == null) {
            throw new BusinessException(ErrorCode.SIMULATION_TASK_NOT_FOUND, "仿真任务不存在: " + taskId);
        }

        List<SimulationFrameReviewEntity> toUpdate;

        if ("all_unmodified".equals(request.mode())) {
            // 批量通过所有未修改且待审的帧
            toUpdate = reviewMapper.selectList(
                    Wrappers.<SimulationFrameReviewEntity>lambdaQuery()
                            .eq(SimulationFrameReviewEntity::getTaskId, taskId)
                            .eq(SimulationFrameReviewEntity::getReviewStatus, "PENDING")
                            .eq(SimulationFrameReviewEntity::getIsModified, false));
        } else if ("by_ids".equals(request.mode())) {
            // 按指定ID列表批量通过
            if (request.frameIds() == null || request.frameIds().isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_MISSING, "frame_ids 不能为空");
            }
            toUpdate = reviewMapper.selectList(
                    Wrappers.<SimulationFrameReviewEntity>lambdaQuery()
                            .eq(SimulationFrameReviewEntity::getTaskId, taskId)
                            .in(SimulationFrameReviewEntity::getFrameId, request.frameIds())
                            .eq(SimulationFrameReviewEntity::getReviewStatus, "PENDING"));
        } else {
            throw new BusinessException(ErrorCode.PARAM_ENUM_INVALID, "不支持的 mode: " + request.mode());
        }

        int count = 0;
        OffsetDateTime now = OffsetDateTime.now();
        for (SimulationFrameReviewEntity entity : toUpdate) {
            entity.setReviewStatus("APPROVED");
            entity.setReviewedBy(username);
            entity.setReviewedAt(now);
            // 未修改的帧，correctedDetectionsJson 保持 null
            reviewMapper.updateById(entity);
            count++;
        }

        // 更新任务审核进度
        updateTaskReviewProgress(taskId);

        log.info("批量通过审核 task_id={} mode={} count={} by={}",
                taskId, request.mode(), count, username);

        return count;
    }

    // ============================================================================
    // 内部方法
    // ============================================================================

    /**
     * 更新任务的审核进度字段
     */
    private void updateTaskReviewProgress(String taskId) {
        ReviewStatsDTO stats = getStats(taskId);
        int progress = stats.progressPercent();

        SimulationTask task = taskMapper.selectOne(
                Wrappers.<SimulationTask>lambdaQuery()
                        .eq(SimulationTask::getTaskId, taskId));
        if (task != null) {
            task.setReviewProgress(progress);
            taskMapper.updateById(task);
        }
    }

    /**
     * 解析 result_json 中的帧列表
     */
    @SuppressWarnings("unchecked")
    private List<FrameInfo> parseResultJsonFrames(String resultJson) {
        if (resultJson == null || resultJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            Map<String, Object> result = objectMapper.readValue(resultJson, Map.class);
            List<Map<String, Object>> frames = (List<Map<String, Object>>) result.get("frames");
            if (frames == null) {
                return Collections.emptyList();
            }

            List<FrameInfo> list = new ArrayList<>();
            for (Map<String, Object> frame : frames) {
                String frameId = (String) frame.get("frameId");
                String frameUrl = (String) frame.get("frameUrl");
                Long timestampMs = frame.get("timestampMs") instanceof Number
                        ? ((Number) frame.get("timestampMs")).longValue()
                        : null;

                // 解析 detections
                List<DetectionDTO> detections = new ArrayList<>();
                List<Map<String, Object>> dets = (List<Map<String, Object>>) frame.get("detections");
                if (dets != null) {
                    for (Map<String, Object> d : dets) {
                        detections.add(new DetectionDTO(
                                (Integer) d.get("classId"),
                                (String) d.get("className"),
                                ((Number) d.get("confidence")).doubleValue(),
                                parseBBox((Map<String, Object>) d.get("bbox"))
                        ));
                    }
                }

                list.add(new FrameInfo(frameId, frameUrl, timestampMs, detections));
            }
            return list;
        } catch (JsonProcessingException e) {
            log.error("解析 result_json 失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private BBoxDTO parseBBox(Map<String, Object> bbox) {
        if (bbox == null) return new BBoxDTO(0, 0, 0, 0);
        return new BBoxDTO(
                ((Number) bbox.get("x1")).doubleValue(),
                ((Number) bbox.get("y1")).doubleValue(),
                ((Number) bbox.get("x2")).doubleValue(),
                ((Number) bbox.get("y2")).doubleValue()
        );
    }

    private List<DetectionDTO> parseDetections(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, DetectionDTO.class));
        } catch (JsonProcessingException e) {
            log.warn("解析 detections JSON 失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON 序列化失败", e);
        }
    }

    // ============================================================================
    // 内部记录类
    // ============================================================================

    private record FrameInfo(String frameId, String frameUrl, Long timestampMs,
                             List<DetectionDTO> detections) {}

    public record InitReviewResult(int total, int alreadyExisted, int created) {}
}
