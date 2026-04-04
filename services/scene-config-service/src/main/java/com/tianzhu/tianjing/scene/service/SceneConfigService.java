package com.tianzhu.tianjing.scene.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianzhu.tianjing.common.exception.BusinessException;
import com.tianzhu.tianjing.common.exception.ErrorCode;
import com.tianzhu.tianjing.common.response.PageResult;
import com.tianzhu.tianjing.common.security.TianjingUserDetails;
import com.tianzhu.tianjing.scene.domain.SceneConfig;
import com.tianzhu.tianjing.scene.domain.SceneConfigHistory;
import com.tianzhu.tianjing.scene.dto.RollbackRequest;
import com.tianzhu.tianjing.scene.dto.SceneConfigDetail;
import com.tianzhu.tianjing.scene.dto.SceneConfigRequest;
import com.tianzhu.tianjing.scene.dto.WorkflowSaveRequest;
import com.tianzhu.tianjing.scene.repository.SceneConfigHistoryMapper;
import com.tianzhu.tianjing.scene.repository.SceneConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 场景配置业务服务
 * 规范：API 接口规范 V3.1 §6.2，CLAUDE.md §5.1
 *
 * 关键设计：
 * 1. 乐观锁：MyBatis-Plus @Version 自动校验，冲突抛 OptimisticLockerException → 捕获转 2001
 * 2. 配置变更写入 scene_config_history 历史表
 * 3. ACTIVE 场景 Redis 热加载：tianjing:scene:active:{scene_id}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SceneConfigService {

    private static final String REDIS_SCENE_KEY_PREFIX = "tianjing:scene:active:";

    private final SceneConfigMapper sceneMapper;
    private final SceneConfigHistoryMapper historyMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    // ==============================
    // 查询场景列表
    // ==============================

    public PageResult<SceneConfigDetail> listScenes(int page, int size,
                                                     String factory, String category,
                                                     String status, String priority,
                                                     String keyword) {
        // 前端枚举值 → DB 存储值映射
        String factoryCode = toDbFactory(factory);
        String dbCategory  = toDbCategory(category);
        String dbStatus    = status != null ? status.toUpperCase() : null;
        Page<SceneConfig> pageParam = new Page<>(page, size);
        var result = sceneMapper.selectPageWithFilters(pageParam, factoryCode, dbCategory, dbStatus, priority, keyword);
        List<SceneConfigDetail> items = result.getRecords().stream()
                .map(SceneConfigDetail::from)
                .toList();
        return PageResult.of(result.getTotal(), page, size, items);
    }

    // ==============================
    // 查询单个场景
    // ==============================

    public SceneConfigDetail getScene(String sceneId) {
        SceneConfig scene = findActiveOrThrow(sceneId);
        return SceneConfigDetail.from(scene);
    }

    // ==============================
    // 新增场景
    // ==============================

    @Transactional
    public SceneConfigDetail createScene(SceneConfigRequest request, String operator) {
        SceneConfig scene = new SceneConfig();
        scene.setSceneId(generateSceneId(request.factoryCode()));
        scene.setStatus("DRAFT");
        scene.setCreatedBy(operator);
        applyRequest(scene, request);
        sceneMapper.insert(scene);

        log.info("新增场景 scene_id={} operator={}", scene.getSceneId(), operator);
        return SceneConfigDetail.from(scene);
    }

    // ==============================
    // 更新场景配置（乐观锁 + 历史写入 + Redis 热加载）
    // ==============================

    @Transactional
    public SceneConfigDetail updateScene(String sceneId, SceneConfigRequest request, String operator) {
        SceneConfig scene = findActiveOrThrow(sceneId);

        // 乐观锁版本校验
        if (request.version() == null || !request.version().equals(scene.getVersion())) {
            throw BusinessException.conflict(
                    "客户端版本=" + request.version() + "，数据库版本=" + scene.getVersion());
        }

        // ACTIVE 场景不允许通过此接口直接删除/状态变更（状态变更走 enable/disable）
        String prevStatus = scene.getStatus();
        applyRequest(scene, request);
        scene.setUpdatedBy(operator);

        int rows = sceneMapper.updateById(scene);
        if (rows == 0) {
            throw BusinessException.conflict("并发更新冲突，请刷新后重试");
        }

        // 写入历史表
        writeHistory(scene, "UPDATE", request.changeDesc(), operator);

        // 若场景处于 ACTIVE 状态，更新 Redis 热缓存（<1s 热加载）
        if ("ACTIVE".equals(prevStatus) || "ACTIVE".equals(scene.getStatus())) {
            syncRedisCache(scene);
        }

        log.info("更新场景配置 scene_id={} version={} operator={}", sceneId, scene.getVersion(), operator);
        return SceneConfigDetail.from(sceneMapper.selectById(scene.getId()));
    }

    // ==============================
    // 删除场景（软删除，ACTIVE 场景不允许删除）
    // ==============================

    @Transactional
    public void deleteScene(String sceneId, String operator) {
        SceneConfig scene = findActiveOrThrow(sceneId);
        if ("ACTIVE".equals(scene.getStatus())) {
            throw BusinessException.of(ErrorCode.RESOURCE_STATE_FORBIDDEN, "ACTIVE 场景不可删除，请先禁用");
        }
        sceneMapper.deleteById(scene.getId());
        redisTemplate.delete(REDIS_SCENE_KEY_PREFIX + sceneId);
        log.info("软删除场景 scene_id={} operator={}", sceneId, operator);
    }

    // ==============================
    // 启用场景
    // ==============================

    @Transactional
    public SceneConfigDetail enableScene(String sceneId, String operator) {
        SceneConfig scene = findActiveOrThrow(sceneId);

        // 前置条件：已绑定摄像头 + 已配置生产模型
        if (StringUtils.isBlank(scene.getBoundDeviceCode())) {
            throw BusinessException.of(ErrorCode.SCENE_ENABLE_PRECONDITION, "未绑定摄像头设备");
        }
        if (StringUtils.isBlank(scene.getActiveModelVersionId())) {
            throw BusinessException.of(ErrorCode.SCENE_ENABLE_PRECONDITION, "未配置生产模型版本");
        }

        scene.setStatus("ACTIVE");
        scene.setUpdatedBy(operator);
        sceneMapper.updateById(scene);

        // 写入 Redis 缓存（路由服务热加载）
        syncRedisCache(scene);

        writeHistory(scene, "ENABLE", "启用场景", operator);
        log.info("启用场景 scene_id={} operator={}", sceneId, operator);
        return SceneConfigDetail.from(scene);
    }

    // ==============================
    // 禁用场景
    // ==============================

    @Transactional
    public SceneConfigDetail disableScene(String sceneId, String operator) {
        SceneConfig scene = findActiveOrThrow(sceneId);
        scene.setStatus("INACTIVE");
        scene.setUpdatedBy(operator);
        sceneMapper.updateById(scene);

        // 清除 Redis 缓存
        redisTemplate.delete(REDIS_SCENE_KEY_PREFIX + sceneId);

        writeHistory(scene, "DISABLE", "禁用场景", operator);
        log.info("禁用场景 scene_id={} operator={}", sceneId, operator);
        return SceneConfigDetail.from(scene);
    }

    // ==============================
    // 回滚配置到指定历史版本
    // ==============================

    @Transactional
    public SceneConfigDetail rollbackScene(String sceneId, RollbackRequest request, String operator) {
        SceneConfig scene = findActiveOrThrow(sceneId);

        SceneConfigHistory history = historyMapper.selectByVersion(sceneId, request.targetVersion())
                .orElseThrow(() -> BusinessException.of(ErrorCode.SCENE_NOT_FOUND,
                        "历史版本不存在: " + request.targetVersion()));

        // 将历史快照还原到当前配置
        try {
            SceneConfig snapshot = objectMapper.readValue(history.getSnapshotJson(), SceneConfig.class);
            scene.setAlarmConfigJson(snapshot.getAlarmConfigJson());
            scene.setAlgoParamsJson(snapshot.getAlgoParamsJson());
            scene.setFrameInterval(snapshot.getFrameInterval());
            // 注意：不回滚状态、绑定设备、模型版本等关键字段
        } catch (Exception e) {
            throw BusinessException.of(ErrorCode.INTERNAL_ERROR, "配置快照解析失败");
        }

        scene.setUpdatedBy(operator);
        sceneMapper.updateById(scene);

        writeHistory(scene, "ROLLBACK",
                "回滚至版本 " + request.targetVersion() + ": " + request.rollbackReason(), operator);

        if ("ACTIVE".equals(scene.getStatus())) {
            syncRedisCache(scene);
        }

        log.info("回滚场景配置 scene_id={} target_version={} operator={}", sceneId, request.targetVersion(), operator);
        return SceneConfigDetail.from(sceneMapper.selectById(scene.getId()));
    }

    // ==============================
    // 私有方法
    // ==============================

    private SceneConfig findActiveOrThrow(String sceneId) {
        SceneConfig scene = sceneMapper.selectOne(new LambdaQueryWrapper<SceneConfig>()
                .eq(SceneConfig::getSceneId, sceneId)
                );
        if (scene == null) {
            throw BusinessException.notFound(ErrorCode.SCENE_NOT_FOUND);
        }
        return scene;
    }

    private void applyRequest(SceneConfig scene, SceneConfigRequest req) {
        if (req.sceneName() != null) scene.setSceneName(req.sceneName());
        if (req.factoryCode() != null) scene.setFactoryCode(req.factoryCode());
        if (req.processCode() != null) scene.setProcessCode(req.processCode());
        if (req.category() != null) scene.setCategory(req.category());
        if (req.priority() != null) scene.setPriority(req.priority());
        if (req.frameInterval() != null) scene.setFrameInterval(req.frameInterval());
        if (req.alarmConfigJson() != null) {
            try {
                scene.setAlarmConfigJson(objectMapper.writeValueAsString(req.alarmConfigJson()));
            } catch (Exception e) {
                throw BusinessException.of(ErrorCode.PARAM_JSON_INVALID, "alarm_config_json 格式错误");
            }
        }
        if (req.algoParamsJson() != null) {
            try {
                scene.setAlgoParamsJson(objectMapper.writeValueAsString(req.algoParamsJson()));
            } catch (Exception e) {
                throw BusinessException.of(ErrorCode.PARAM_JSON_INVALID, "algo_params_json 格式错误");
            }
        }
    }

    // ==============================
    // 保存算法编排工作流（独立端点，不触发乐观锁校验）
    // ==============================

    @Transactional
    public SceneConfigDetail saveWorkflow(String sceneId, WorkflowSaveRequest request, String operator) {
        SceneConfig scene = findActiveOrThrow(sceneId);
        try {
            scene.setWorkflowJson(objectMapper.writeValueAsString(request.workflowJson()));
        } catch (Exception e) {
            throw BusinessException.of(ErrorCode.PARAM_JSON_INVALID, "workflowJson 格式错误");
        }
        scene.setUpdatedBy(operator);
        sceneMapper.updateById(scene);
        writeHistory(scene, "WORKFLOW_UPDATE", "保存算法编排", operator);
        log.info("保存工作流编排 scene_id={} operator={}", sceneId, operator);
        return SceneConfigDetail.from(sceneMapper.selectById(scene.getId()));
    }

    // ==============================
    // 查询配置变更历史（接口 #11）
    // ==============================

    public PageResult<SceneConfigHistory> listHistory(String sceneId, int page, int size) {
        findActiveOrThrow(sceneId); // 校验场景存在
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SceneConfigHistory> pageParam =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
        var result = historyMapper.selectPage(pageParam,
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SceneConfigHistory>()
                        .eq(SceneConfigHistory::getSceneId, sceneId)
                        .orderByDesc(SceneConfigHistory::getConfigVersion));
        return PageResult.of(result.getTotal(), page, size, result.getRecords());
    }

    private void writeHistory(SceneConfig scene, String changeType, String changeDesc, String operator) {
        Integer maxVersion = historyMapper.selectMaxVersion(scene.getSceneId());
        int nextVersion = (maxVersion == null ? 0 : maxVersion) + 1;

        SceneConfigHistory history = new SceneConfigHistory();
        history.setSceneId(scene.getSceneId());
        history.setConfigVersion(nextVersion);
        history.setChangeType(changeType);
        history.setChangeDesc(changeDesc);
        history.setChangedBy(operator);
        history.setChangedAt(java.time.OffsetDateTime.now());

        try {
            history.setSnapshotJson(objectMapper.writeValueAsString(scene));
        } catch (Exception e) {
            log.warn("配置快照序列化失败，跳过历史写入: {}", e.getMessage());
        }

        historyMapper.insert(history);
    }

    private void syncRedisCache(SceneConfig scene) {
        try {
            String json = objectMapper.writeValueAsString(SceneConfigDetail.from(scene));
            redisTemplate.opsForValue().set(REDIS_SCENE_KEY_PREFIX + scene.getSceneId(), json);
            log.debug("Redis 缓存已更新 key={}", REDIS_SCENE_KEY_PREFIX + scene.getSceneId());
        } catch (Exception e) {
            // 缓存更新失败不影响主流程（路由服务有 DB 降级读取）
            log.warn("Redis 缓存更新失败 scene_id={}: {}", scene.getSceneId(), e.getMessage());
        }
    }

    /** 前端 Factory 枚举值 → DB factory_code */
    private static String toDbFactory(String frontendValue) {
        if (frontendValue == null) return null;
        return switch (frontendValue.toLowerCase()) {
            case "pellet"    -> "PELLET";
            case "sintering" -> "SINTER";
            case "steel"     -> "STEEL";
            case "section"   -> "SECTION";
            case "strip"     -> "STRIP";
            default          -> frontendValue.toUpperCase();
        };
    }

    /** 前端 SceneCategory 枚举值 → DB category */
    private static String toDbCategory(String frontendValue) {
        if (frontendValue == null) return null;
        return switch (frontendValue.toLowerCase()) {
            case "quality"   -> "QUALITY_INSPECT";
            case "equipment" -> "EQUIPMENT_MONITOR";
            case "process"   -> "PROCESS_PARAM";
            default          -> frontendValue.toUpperCase();
        };
    }

    private String generateSceneId(String factoryCode) {
        String prefix = switch (factoryCode.toUpperCase()) {
            case "PELLET"  -> "SCENE-PELLET-";
            case "SINTER"  -> "SCENE-SINTER-";
            case "STEEL"   -> "SCENE-STEEL-";
            case "SECTION" -> "SCENE-SECTION-";
            case "STRIP"   -> "SCENE-STRIP-";
            default        -> "SCENE-";
        };
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}
