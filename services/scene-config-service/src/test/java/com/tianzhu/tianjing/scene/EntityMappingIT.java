package com.tianzhu.tianjing.scene;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianzhu.tianjing.scene.domain.SceneConfig;
import com.tianzhu.tianjing.scene.domain.SceneConfigHistory;
import com.tianzhu.tianjing.scene.repository.SceneConfigHistoryMapper;
import com.tianzhu.tianjing.scene.repository.SceneConfigMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Entity 字段映射集成测试 — scene-config-service
 * 防止 @TableField 列名与数据库实际列名不对应。
 * 每次 Flyway Migration 变更后，CI 自动运行此测试。
 * 运行方式：mvn verify -Pintegration-test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("it")
@Testcontainers
@DisplayName("Entity 字段映射集成测试 — scene-config-service")
class EntityMappingIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tianjing_prod")
            .withUsername("tianjing_app")
            .withPassword("test_password");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // CommonSecurityConfig 通过构造器注入 StringRedisTemplate，此处提供 Mock 替代真实 Redis 连接
    @MockBean
    StringRedisTemplate redisTemplate;

    @Autowired
    SceneConfigMapper sceneConfigMapper;

    @Autowired
    SceneConfigHistoryMapper sceneConfigHistoryMapper;

    @Test
    @DisplayName("SceneConfig — 所有 @TableField 列名映射正确（SELECT 不抛 BadSqlGrammarException）")
    void sceneConfig_selectMappingCorrect() {
        assertThatNoException().isThrownBy(() ->
                sceneConfigMapper.selectOne(
                        new LambdaQueryWrapper<SceneConfig>().last("LIMIT 1")));
    }

    @Test
    @DisplayName("SceneConfig — INSERT 后 SELECT 验证字段完整回写")
    void sceneConfig_insertAndSelectRoundTrip() {
        SceneConfig config = new SceneConfig();
        config.setSceneId("SCENE-IT-" + System.currentTimeMillis());
        config.setSceneName("集成测试场景");
        config.setFactoryCode("SINTER");
        config.setProcessCode("SINTER-MAIN");
        config.setCategory("detection");
        config.setPriority("HIGH");
        config.setStatus("DRAFT");
        config.setFrameInterval(40);
        config.setVersion(1);
        config.setIsDeleted(false);
        config.setCreatedBy("it-test");
        config.setUpdatedBy("it-test");

        assertThatNoException().isThrownBy(() -> sceneConfigMapper.insert(config));

        SceneConfig loaded = sceneConfigMapper.selectById(config.getId());
        org.assertj.core.api.Assertions.assertThat(loaded).isNotNull();
        org.assertj.core.api.Assertions.assertThat(loaded.getSceneId()).isEqualTo(config.getSceneId());
        org.assertj.core.api.Assertions.assertThat(loaded.getFactoryCode()).isEqualTo("SINTER");
        org.assertj.core.api.Assertions.assertThat(loaded.getFrameInterval()).isEqualTo(40);
    }

    @Test
    @DisplayName("SceneConfigHistory — 所有 @TableField 列名映射正确")
    void sceneConfigHistory_selectMappingCorrect() {
        assertThatNoException().isThrownBy(() ->
                sceneConfigHistoryMapper.selectOne(
                        new LambdaQueryWrapper<SceneConfigHistory>().last("LIMIT 1")));
    }
}
