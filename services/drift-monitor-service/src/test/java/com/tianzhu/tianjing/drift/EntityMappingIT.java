package com.tianzhu.tianjing.drift;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianzhu.tianjing.drift.domain.DataSyncAudit;
import com.tianzhu.tianjing.drift.domain.DriftMetric;
import com.tianzhu.tianjing.drift.domain.SysOperationLog;
import com.tianzhu.tianjing.drift.domain.SysUserView;
import com.tianzhu.tianjing.drift.repository.DataSyncAuditMapper;
import com.tianzhu.tianjing.drift.repository.DriftMetricMapper;
import com.tianzhu.tianjing.drift.repository.SysOperationLogMapper;
import com.tianzhu.tianjing.drift.repository.SysUserViewMapper;
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

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Entity 字段映射集成测试 — drift-monitor-service
 * 验证 DriftMetric、DataSyncAudit、SysOperationLog、SysUserView 列名映射。
 *
 * 注意：TrainJob 映射的 train_job 表位于 tianjing_train 库（独立 Flyway），
 * 不在 tianjing_prod Schema 中，故跳过 TrainJob 的映射测试（见 CLAUDE.md §4.2 训推分离规定）。
 *
 * 运行方式：mvn verify -Pintegration-test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("it")
@Testcontainers
@DisplayName("Entity 字段映射集成测试 — drift-monitor-service")
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
    DriftMetricMapper driftMetricMapper;

    @Autowired
    DataSyncAuditMapper dataSyncAuditMapper;

    @Autowired
    SysOperationLogMapper sysOperationLogMapper;

    @Autowired
    SysUserViewMapper sysUserViewMapper;

    @Test
    @DisplayName("DriftMetric — 所有 @TableField 列名映射正确")
    void driftMetric_selectMappingCorrect() {
        assertThatNoException().isThrownBy(() ->
                driftMetricMapper.selectOne(
                        new LambdaQueryWrapper<DriftMetric>().last("LIMIT 1")));
    }

    @Test
    @DisplayName("DriftMetric — INSERT 后 SELECT 验证字段完整回写")
    void driftMetric_insertAndSelectRoundTrip() {
        DriftMetric metric = new DriftMetric();
        metric.setMetricId("DM-IT-" + System.currentTimeMillis());
        metric.setSceneId("SCENE-IT-001");
        metric.setModelVersionId("MODEL-IT-001");
        metric.setMetricDate(LocalDate.now());
        metric.setPrecisionVal(0.91);
        metric.setRecallVal(0.88);
        metric.setIsBelowThreshold(false);
        metric.setConsecutiveBelow(0);
        metric.setCreatedBy("it-test");

        assertThatNoException().isThrownBy(() -> driftMetricMapper.insert(metric));

        DriftMetric loaded = driftMetricMapper.selectById(metric.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getMetricId()).isEqualTo(metric.getMetricId());
        assertThat(loaded.getIsBelowThreshold()).isFalse();
    }

    @Test
    @DisplayName("DataSyncAudit — 所有 @TableField 列名映射正确（分区表）")
    void dataSyncAudit_selectMappingCorrect() {
        assertThatNoException().isThrownBy(() ->
                dataSyncAuditMapper.selectOne(
                        new LambdaQueryWrapper<DataSyncAudit>().last("LIMIT 1")));
    }

    @Test
    @DisplayName("DataSyncAudit — INSERT 到分区表，字段均正确写入")
    void dataSyncAudit_insertMappingCorrect() {
        DataSyncAudit audit = new DataSyncAudit();
        audit.setSyncBatchId("BATCH-IT-" + System.currentTimeMillis());
        audit.setSceneId("SCENE-IT-001");
        audit.setFactoryCode("SINTER");
        audit.setImageCount(100);
        audit.setDesensitizeRule("MASK_TIMESTAMPS");
        audit.setTotalSizeMb(1.5);
        audit.setSourceMinioPprefix("sintering/SCENE-IT-001/2026-04/");
        audit.setDestMinioPrefix("train/SCENE-IT-001/2026-04/");
        audit.setGapDeviceId("GAP-DEVICE-IT-001");
        audit.setSyncStatus("SUCCESS");
        audit.setSyncedAt(OffsetDateTime.now());
        audit.setOperator("it-test");

        assertThatNoException().isThrownBy(() -> dataSyncAuditMapper.insert(audit));
    }

    @Test
    @DisplayName("SysOperationLog — 所有 @TableField 列名映射正确（分区表）")
    void sysOperationLog_selectMappingCorrect() {
        assertThatNoException().isThrownBy(() ->
                sysOperationLogMapper.selectOne(
                        new LambdaQueryWrapper<SysOperationLog>().last("LIMIT 1")));
    }

    @Test
    @DisplayName("SysUserView — 所有 @TableField 列名映射正确（读 sys_user 视图）")
    void sysUserView_selectMappingCorrect() {
        assertThatNoException().isThrownBy(() ->
                sysUserViewMapper.selectOne(
                        new LambdaQueryWrapper<SysUserView>().last("LIMIT 1")));
    }
}
