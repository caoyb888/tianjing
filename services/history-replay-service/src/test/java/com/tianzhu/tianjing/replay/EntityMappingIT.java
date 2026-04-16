package com.tianzhu.tianjing.replay;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianzhu.tianjing.replay.domain.SimulationTask;
import com.tianzhu.tianjing.replay.repository.SimulationTaskMapper;
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

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Entity 字段映射集成测试 — history-replay-service
 * 验证 SimulationTask 所有 @TableField 列名与 DB 对应。
 * MinioClient 由 MinioConfig 以占位地址创建（不实际连接），无需 Mock。
 * 运行方式：mvn verify -Pintegration-test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("it")
@Testcontainers
@DisplayName("Entity 字段映射集成测试 — history-replay-service")
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
    SimulationTaskMapper simulationTaskMapper;

    @Test
    @DisplayName("SimulationTask — 所有 @TableField 列名映射正确")
    void simulationTask_selectMappingCorrect() {
        assertThatNoException().isThrownBy(() ->
                simulationTaskMapper.selectOne(
                        new LambdaQueryWrapper<SimulationTask>().last("LIMIT 1")));
    }

    @Test
    @DisplayName("SimulationTask — INSERT 后 SELECT 验证字段完整回写")
    void simulationTask_insertAndSelectRoundTrip() {
        SimulationTask task = new SimulationTask();
        task.setTaskId("SIM-IT-" + System.currentTimeMillis());
        task.setSceneId("SCENE-IT-001");
        task.setTaskName("it-test-video.mp4");
        task.setVideoFileUrl("simulation/SCENE-IT-001/uuid/it-test-video.mp4");
        task.setStatus("PENDING");
        task.setStartedAt(OffsetDateTime.now());
        task.setCreatedBy("it-test");
        task.setUpdatedBy("it-test");

        assertThatNoException().isThrownBy(() -> simulationTaskMapper.insert(task));

        SimulationTask loaded = simulationTaskMapper.selectById(task.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getTaskId()).isEqualTo(task.getTaskId());
        assertThat(loaded.getTaskName()).isEqualTo("it-test-video.mp4");
        assertThat(loaded.getStatus()).isEqualTo("PENDING");
        // 验证 videoFileUrl 与 startedAt 列名映射正确
        assertThat(loaded.getVideoFileUrl()).isEqualTo(task.getVideoFileUrl());
        assertThat(loaded.getStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("SimulationTask — 按 taskId 查询（getTaskProgress 核心路径）")
    void simulationTask_selectByTaskId() {
        assertThatNoException().isThrownBy(() ->
                simulationTaskMapper.selectOne(
                        new LambdaQueryWrapper<SimulationTask>()
                                .eq(SimulationTask::getTaskId, "SIM-NONEXISTENT")));
    }
}
