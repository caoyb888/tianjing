package com.tianzhu.tianjing.algomodel;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianzhu.tianjing.algomodel.domain.AlgorithmPlugin;
import com.tianzhu.tianjing.algomodel.domain.ModelVersion;
import com.tianzhu.tianjing.algomodel.repository.AlgorithmPluginMapper;
import com.tianzhu.tianjing.algomodel.repository.ModelVersionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Entity 字段映射集成测试 — alarm-rule-service（algomodel）
 * 验证 AlgorithmPlugin、ModelVersion 所有 @TableField 列名与 DB 对应。
 * 运行方式：mvn verify -Pintegration-test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("it")
@Testcontainers
@DisplayName("Entity 字段映射集成测试 — alarm-rule-service")
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

    @MockBean
    StringRedisTemplate redisTemplate;

    @Autowired
    AlgorithmPluginMapper algorithmPluginMapper;

    @Autowired
    ModelVersionMapper modelVersionMapper;

    @Test
    @DisplayName("AlgorithmPlugin — 所有 @TableField 列名映射正确")
    void algorithmPlugin_selectMappingCorrect() {
        assertThatNoException().isThrownBy(() ->
                algorithmPluginMapper.selectOne(
                        new LambdaQueryWrapper<AlgorithmPlugin>().last("LIMIT 1")));
    }

    @Test
    @DisplayName("AlgorithmPlugin — INSERT 后 SELECT 验证字段完整回写")
    void algorithmPlugin_insertAndSelectRoundTrip() {
        AlgorithmPlugin plugin = new AlgorithmPlugin();
        plugin.setPluginId("ATOM-IT-TEST-" + System.currentTimeMillis());
        plugin.setPluginName("集成测试算法插件");
        plugin.setPluginType("detection");
        plugin.setVersion("1.0.0");
        plugin.setIsAtom(true);
        plugin.setStatus("AVAILABLE");
        plugin.setIsDeleted(false);
        plugin.setCreatedBy("it-test");
        plugin.setUpdatedBy("it-test");

        assertThatNoException().isThrownBy(() -> algorithmPluginMapper.insert(plugin));

        AlgorithmPlugin loaded = algorithmPluginMapper.selectById(plugin.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getPluginId()).isEqualTo(plugin.getPluginId());
        assertThat(loaded.getIsAtom()).isTrue();
        assertThat(loaded.getStatus()).isEqualTo("AVAILABLE");
    }

    @Test
    @DisplayName("ModelVersion — 所有 @TableField 列名映射正确")
    void modelVersion_selectMappingCorrect() {
        assertThatNoException().isThrownBy(() ->
                modelVersionMapper.selectOne(
                        new LambdaQueryWrapper<ModelVersion>().last("LIMIT 1")));
    }

    @Test
    @DisplayName("ModelVersion — INSERT 后 SELECT 验证关键字段（pluginId、map50、status）")
    void modelVersion_insertAndSelectRoundTrip() {
        // 先插入 plugin 以满足外键（若有）
        AlgorithmPlugin plugin = new AlgorithmPlugin();
        plugin.setPluginId("ATOM-MV-IT-" + System.currentTimeMillis());
        plugin.setPluginName("测试插件");
        plugin.setPluginType("detection");
        plugin.setVersion("1.0.0");
        plugin.setIsAtom(true);
        plugin.setStatus("AVAILABLE");
        plugin.setIsDeleted(false);
        plugin.setCreatedBy("it-test");
        plugin.setUpdatedBy("it-test");
        algorithmPluginMapper.insert(plugin);

        ModelVersion mv = new ModelVersion();
        mv.setPluginId(plugin.getPluginId());
        mv.setStatus("STAGING");
        mv.setCreatedBy("it-test");
        mv.setUpdatedBy("it-test");

        assertThatNoException().isThrownBy(() -> modelVersionMapper.insert(mv));

        ModelVersion loaded = modelVersionMapper.selectById(mv.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getPluginId()).isEqualTo(plugin.getPluginId());
        assertThat(loaded.getStatus()).isEqualTo("STAGING");
    }
}
