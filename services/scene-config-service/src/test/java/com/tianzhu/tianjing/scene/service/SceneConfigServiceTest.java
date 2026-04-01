package com.tianzhu.tianjing.scene.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianzhu.tianjing.common.exception.BusinessException;
import com.tianzhu.tianjing.common.exception.ErrorCode;
import com.tianzhu.tianjing.scene.domain.SceneConfig;
import com.tianzhu.tianjing.scene.dto.SceneConfigDetail;
import com.tianzhu.tianjing.scene.dto.SceneConfigRequest;
import com.tianzhu.tianjing.scene.repository.SceneConfigHistoryMapper;
import com.tianzhu.tianjing.scene.repository.SceneConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SceneConfigService 单元测试
 * 覆盖：getScene / createScene / updateScene / deleteScene / enableScene / disableScene
 * 规范：CLAUDE.md §12.1，Service 层覆盖率 ≥ 80%
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SceneConfigService — 场景配置业务服务单元测试")
class SceneConfigServiceTest {

    @Mock private SceneConfigMapper sceneMapper;
    @Mock private SceneConfigHistoryMapper historyMapper;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private SceneConfigService sceneConfigService;

    @BeforeEach
    void setUp() {
        sceneConfigService = new SceneConfigService(
                sceneMapper, historyMapper, redisTemplate, new ObjectMapper());
    }

    // ========== getScene() ==========

    @Test
    @DisplayName("getScene — 场景存在，正常返回 SceneConfigDetail")
    void getScene_exists_returnsDetail() {
        SceneConfig scene = buildScene("SCENE-SINTER-001", "ACTIVE");
        when(sceneMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(scene);

        SceneConfigDetail detail = sceneConfigService.getScene("SCENE-SINTER-001");

        assertThat(detail.sceneId()).isEqualTo("SCENE-SINTER-001");
        assertThat(detail.status()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("getScene — 场景不存在，抛出 SCENE_NOT_FOUND")
    void getScene_notFound_throwsSceneNotFound() {
        when(sceneMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> sceneConfigService.getScene("SCENE-GHOST-999"))
                .satisfies(e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SCENE_NOT_FOUND));
    }

    // ========== createScene() ==========

    @Test
    @DisplayName("createScene — 正常创建，状态默认 DRAFT")
    void createScene_validRequest_createsWithDraftStatus() {
        SceneConfigRequest req = buildRequest("SINTER", 1);
        when(sceneMapper.insert(any())).thenReturn(1);

        SceneConfigDetail detail = sceneConfigService.createScene(req, "operator1");

        assertThat(detail.status()).isEqualTo("DRAFT");
        assertThat(detail.sceneId()).matches("SCENE-SINTER-[A-Z0-9]{6}");
        assertThat(detail.createdBy()).isEqualTo("operator1");
        verify(sceneMapper).insert(any(SceneConfig.class));
    }

    @Test
    @DisplayName("createScene — 厂部编码映射正确（PELLET / STEEL / SECTION / STRIP）")
    void createScene_factoryCodes_mappedCorrectly() {
        String[] codes = {"PELLET", "STEEL", "SECTION", "STRIP"};
        String[] prefixes = {"SCENE-PELLET-", "SCENE-STEEL-", "SCENE-SECTION-", "SCENE-STRIP-"};

        for (int i = 0; i < codes.length; i++) {
            when(sceneMapper.insert(any())).thenReturn(1);
            SceneConfigDetail detail = sceneConfigService.createScene(buildRequest(codes[i], 1), "op");
            assertThat(detail.sceneId()).startsWith(prefixes[i]);
        }
    }

    // ========== updateScene() ==========

    @Test
    @DisplayName("updateScene — 版本匹配，正常更新")
    void updateScene_versionMatch_updatesSuccessfully() {
        SceneConfig scene = buildScene("SCENE-SINTER-001", "DRAFT");
        scene.setVersion(3);
        when(sceneMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(scene);
        when(sceneMapper.updateById(any())).thenReturn(1);
        when(sceneMapper.selectById(any())).thenReturn(scene);
        when(historyMapper.selectMaxVersion(anyString())).thenReturn(2);
        when(historyMapper.insert(any())).thenReturn(1);

        SceneConfigRequest req = buildRequest("SINTER", 3);
        SceneConfigDetail detail = sceneConfigService.updateScene("SCENE-SINTER-001", req, "editor");

        assertThat(detail).isNotNull();
        verify(sceneMapper).updateById(any(SceneConfig.class));
        verify(historyMapper).insert(any()); // 历史写入
    }

    @Test
    @DisplayName("updateScene — 版本不匹配，抛出 OPTIMISTIC_LOCK_CONFLICT")
    void updateScene_versionMismatch_throwsConflict() {
        SceneConfig scene = buildScene("SCENE-SINTER-001", "DRAFT");
        scene.setVersion(5);
        when(sceneMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(scene);

        SceneConfigRequest req = buildRequest("SINTER", 3); // 提交版本 3，DB 版本 5

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> sceneConfigService.updateScene("SCENE-SINTER-001", req, "editor"))
                .satisfies(e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OPTIMISTIC_LOCK_CONFLICT));
    }

    @Test
    @DisplayName("updateScene — version 为 null，抛出 OPTIMISTIC_LOCK_CONFLICT")
    void updateScene_nullVersion_throwsConflict() {
        SceneConfig scene = buildScene("SCENE-SINTER-001", "DRAFT");
        scene.setVersion(1);
        when(sceneMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(scene);

        SceneConfigRequest req = new SceneConfigRequest("测试场景", "SINTER", null, "detection",
                "HIGH", 40, null, null, null, "null version");

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> sceneConfigService.updateScene("SCENE-SINTER-001", req, "editor"))
                .satisfies(e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OPTIMISTIC_LOCK_CONFLICT));
    }

    @Test
    @DisplayName("updateScene — 并发写失败（updateById 返回 0），抛出冲突")
    void updateScene_concurrentWrite_throwsConflict() {
        SceneConfig scene = buildScene("SCENE-SINTER-001", "DRAFT");
        scene.setVersion(2);
        when(sceneMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(scene);
        when(sceneMapper.updateById(any())).thenReturn(0); // 并发冲突

        SceneConfigRequest req = buildRequest("SINTER", 2);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> sceneConfigService.updateScene("SCENE-SINTER-001", req, "editor"))
                .satisfies(e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.OPTIMISTIC_LOCK_CONFLICT));
    }

    @Test
    @DisplayName("updateScene — ACTIVE 场景更新后同步 Redis 缓存")
    void updateScene_activeScene_syncesRedisCache() {
        SceneConfig scene = buildScene("SCENE-SINTER-001", "ACTIVE");
        scene.setVersion(1);
        when(sceneMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(scene);
        when(sceneMapper.updateById(any())).thenReturn(1);
        when(sceneMapper.selectById(any())).thenReturn(scene);
        when(historyMapper.selectMaxVersion(anyString())).thenReturn(0);
        when(historyMapper.insert(any())).thenReturn(1);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        sceneConfigService.updateScene("SCENE-SINTER-001", buildRequest("SINTER", 1), "editor");

        verify(valueOps).set(eq("tianjing:scene:active:SCENE-SINTER-001"), anyString());
    }

    // ========== deleteScene() ==========

    @Test
    @DisplayName("deleteScene — DRAFT 场景可以软删除")
    void deleteScene_draftScene_deletesSuccessfully() {
        SceneConfig scene = buildScene("SCENE-SINTER-001", "DRAFT");
        when(sceneMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(scene);
        when(sceneMapper.deleteById(any(Long.class))).thenReturn(1);
        when(redisTemplate.delete(anyString())).thenReturn(true);

        assertThatNoException()
                .isThrownBy(() -> sceneConfigService.deleteScene("SCENE-SINTER-001", "admin"));

        verify(sceneMapper).deleteById(scene.getId());
        verify(redisTemplate).delete("tianjing:scene:active:SCENE-SINTER-001");
    }

    @Test
    @DisplayName("deleteScene — ACTIVE 场景不允许删除，抛出 RESOURCE_STATE_FORBIDDEN")
    void deleteScene_activeScene_throwsStateForbidden() {
        SceneConfig scene = buildScene("SCENE-SINTER-001", "ACTIVE");
        when(sceneMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(scene);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> sceneConfigService.deleteScene("SCENE-SINTER-001", "admin"))
                .satisfies(e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_STATE_FORBIDDEN));

        verify(sceneMapper, never()).deleteById(any(Long.class));
    }

    // ========== enableScene() ==========

    @Test
    @DisplayName("enableScene — 已绑定设备和模型，正常启用，Redis 写入缓存")
    void enableScene_withDeviceAndModel_enablesAndCachesInRedis() {
        SceneConfig scene = buildScene("SCENE-SINTER-001", "DRAFT");
        scene.setBoundDeviceCode("CAM-001");
        scene.setActiveModelVersionId("MODEL-V3");
        when(sceneMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(scene);
        when(sceneMapper.updateById(any())).thenReturn(1);
        when(historyMapper.selectMaxVersion(anyString())).thenReturn(0);
        when(historyMapper.insert(any())).thenReturn(1);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        SceneConfigDetail detail = sceneConfigService.enableScene("SCENE-SINTER-001", "admin");

        assertThat(detail.status()).isEqualTo("ACTIVE");
        verify(valueOps).set(eq("tianjing:scene:active:SCENE-SINTER-001"), anyString());
    }

    @Test
    @DisplayName("enableScene — 未绑定摄像头，抛出 SCENE_ENABLE_PRECONDITION")
    void enableScene_noDevice_throwsPrecondition() {
        SceneConfig scene = buildScene("SCENE-SINTER-001", "DRAFT");
        scene.setBoundDeviceCode(null);
        scene.setActiveModelVersionId("MODEL-V3");
        when(sceneMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(scene);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> sceneConfigService.enableScene("SCENE-SINTER-001", "admin"))
                .satisfies(e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SCENE_ENABLE_PRECONDITION));
    }

    @Test
    @DisplayName("enableScene — 未配置生产模型，抛出 SCENE_ENABLE_PRECONDITION")
    void enableScene_noModel_throwsPrecondition() {
        SceneConfig scene = buildScene("SCENE-SINTER-001", "DRAFT");
        scene.setBoundDeviceCode("CAM-001");
        scene.setActiveModelVersionId(null);
        when(sceneMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(scene);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> sceneConfigService.enableScene("SCENE-SINTER-001", "admin"))
                .satisfies(e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SCENE_ENABLE_PRECONDITION));
    }

    // ========== disableScene() ==========

    @Test
    @DisplayName("disableScene — 正常禁用，状态改为 INACTIVE，清除 Redis 缓存")
    void disableScene_activeScene_setsInactiveAndClearsRedis() {
        SceneConfig scene = buildScene("SCENE-SINTER-001", "ACTIVE");
        when(sceneMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(scene);
        when(sceneMapper.updateById(any())).thenReturn(1);
        when(historyMapper.selectMaxVersion(anyString())).thenReturn(0);
        when(historyMapper.insert(any())).thenReturn(1);
        when(redisTemplate.delete(anyString())).thenReturn(true);

        SceneConfigDetail detail = sceneConfigService.disableScene("SCENE-SINTER-001", "admin");

        assertThat(detail.status()).isEqualTo("INACTIVE");
        verify(redisTemplate).delete("tianjing:scene:active:SCENE-SINTER-001");
    }

    @Test
    @DisplayName("disableScene — 场景不存在，抛出 SCENE_NOT_FOUND")
    void disableScene_notFound_throwsSceneNotFound() {
        when(sceneMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> sceneConfigService.disableScene("SCENE-GHOST", "admin"))
                .satisfies(e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SCENE_NOT_FOUND));
    }

    // ========== 工厂方法 ==========

    private SceneConfig buildScene(String sceneId, String status) {
        SceneConfig scene = new SceneConfig();
        scene.setId(1L);
        scene.setSceneId(sceneId);
        scene.setSceneName("测试烧结场景");
        scene.setFactoryCode("SINTER");
        scene.setProcessCode("SINTER-MAIN");
        scene.setCategory("detection");
        scene.setPriority("HIGH");
        scene.setStatus(status);
        scene.setFrameInterval(40);
        scene.setVersion(1);
        scene.setIsDeleted(0);
        scene.setCreatedAt(OffsetDateTime.now());
        scene.setUpdatedAt(OffsetDateTime.now());
        scene.setCreatedBy("tester");
        return scene;
    }

    private SceneConfigRequest buildRequest(String factoryCode, int version) {
        return new SceneConfigRequest(
                "测试场景", factoryCode, "MAIN", "detection",
                "HIGH", 40, null, null, version, "测试变更"
        );
    }
}
