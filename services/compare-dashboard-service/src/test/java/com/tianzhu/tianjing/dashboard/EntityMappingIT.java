package com.tianzhu.tianjing.dashboard;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianzhu.tianjing.dashboard.domain.SandboxCompareReport;
import com.tianzhu.tianjing.dashboard.domain.SandboxSession;
import com.tianzhu.tianjing.dashboard.repository.SandboxCompareReportMapper;
import com.tianzhu.tianjing.dashboard.repository.SandboxSessionMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Entity 字段映射集成测试 — compare-dashboard-service
 * 验证 SandboxSession、SandboxCompareReport 所有 @TableField 列名与 DB 对应。
 *
 * 特殊说明：两个 Entity 使用 @TableName(schema="tianjing_sandbox")，
 * MyBatis-Plus 生成 SQL 带 schema 前缀（tianjing_sandbox.table_name）。
 * 通过 spring.flyway.schemas=tianjing_sandbox 在容器内创建对应 PostgreSQL Schema，
 * 并将 Sandbox Flyway 迁移运行在该 Schema 下，确保与生产 Schema 结构一致。
 *
 * 运行方式：mvn verify -Pintegration-test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("it")
@Testcontainers
@DisplayName("Entity 字段映射集成测试 — compare-dashboard-service")
class EntityMappingIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tianjing_test")
            .withUsername("tianjing_sandbox_app")
            .withPassword("test_password");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // KafkaAutoConfiguration 已在 application-it.yml 排除；
    // SandboxService 通过构造器注入 KafkaTemplate，此处提供 Mock
    @MockBean
    @SuppressWarnings("rawtypes")
    KafkaTemplate kafkaTemplate;

    @MockBean
    StringRedisTemplate redisTemplate;

    @Autowired
    SandboxSessionMapper sandboxSessionMapper;

    @Autowired
    SandboxCompareReportMapper sandboxCompareReportMapper;

    @Test
    @DisplayName("SandboxSession — 所有 @TableField 列名映射正确（tianjing_sandbox schema）")
    void sandboxSession_selectMappingCorrect() {
        assertThatNoException().isThrownBy(() ->
                sandboxSessionMapper.selectOne(
                        new LambdaQueryWrapper<SandboxSession>().last("LIMIT 1")));
    }

    @Test
    @DisplayName("SandboxSession — INSERT 后 SELECT 验证关键字段")
    void sandboxSession_insertAndSelectRoundTrip() {
        SandboxSession session = new SandboxSession();
        session.setSessionId("SES-IT-" + System.currentTimeMillis());
        session.setSceneId("SCENE-IT-001");
        session.setCandidateModelId("MODEL-CANDIDATE-IT");
        session.setProdModelId("MODEL-PROD-IT");
        session.setMirrorFps(5);
        session.setStatus("RUNNING");
        session.setTotalFrames(0);
        session.setTotalAnomalyFrames(0);
        session.setCreatedBy("it-test");

        assertThatNoException().isThrownBy(() -> sandboxSessionMapper.insert(session));

        SandboxSession loaded = sandboxSessionMapper.selectById(session.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getSessionId()).isEqualTo(session.getSessionId());
        assertThat(loaded.getMirrorFps()).isEqualTo(5);
        assertThat(loaded.getStatus()).isEqualTo("RUNNING");
    }

    @Test
    @DisplayName("SandboxCompareReport — 所有 @TableField 列名映射正确")
    void sandboxCompareReport_selectMappingCorrect() {
        assertThatNoException().isThrownBy(() ->
                sandboxCompareReportMapper.selectOne(
                        new LambdaQueryWrapper<SandboxCompareReport>().last("LIMIT 1")));
    }

    @Test
    @DisplayName("SandboxCompareReport — INSERT 后 SELECT 验证关键字段")
    void sandboxCompareReport_insertAndSelectRoundTrip() {
        // 先插入 session 以满足外键（若有）
        SandboxSession session = new SandboxSession();
        session.setSessionId("SES-RPT-IT-" + System.currentTimeMillis());
        session.setSceneId("SCENE-IT-001");
        session.setCandidateModelId("MODEL-CANDIDATE-IT");
        session.setProdModelId("MODEL-PROD-IT");
        session.setMirrorFps(5);
        session.setStatus("COMPLETED");
        session.setTotalFrames(1000);
        session.setTotalAnomalyFrames(50);
        session.setCreatedBy("it-test");
        sandboxSessionMapper.insert(session);

        SandboxCompareReport report = new SandboxCompareReport();
        report.setReportId("RPT-IT-" + System.currentTimeMillis());
        report.setSessionId(session.getSessionId());
        report.setSceneId("SCENE-IT-001");
        report.setHoursEvaluated(48.0);
        report.setSandboxPrecision(0.93);
        report.setProdPrecision(0.91);
        report.setPromoteStatus("PENDING");

        assertThatNoException().isThrownBy(() -> sandboxCompareReportMapper.insert(report));

        SandboxCompareReport loaded = sandboxCompareReportMapper.selectById(report.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getSessionId()).isEqualTo(session.getSessionId());
        assertThat(loaded.getSandboxPrecision()).isEqualTo(0.93);
    }
}
