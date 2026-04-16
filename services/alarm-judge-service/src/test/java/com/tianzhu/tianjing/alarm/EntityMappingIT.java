package com.tianzhu.tianjing.alarm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianzhu.tianjing.alarm.domain.AlarmRecord;
import com.tianzhu.tianjing.alarm.repository.AlarmRecordMapper;
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

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Entity 字段映射集成测试 — alarm-judge-service
 * 验证 AlarmRecord 所有 @TableField 列名与 DB 对应。
 * alarm_record 是分区表，SELECT 需要路由到正确分区。
 * 运行方式：mvn verify -Pintegration-test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("it")
@Testcontainers
@DisplayName("Entity 字段映射集成测试 — alarm-judge-service")
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

    // KafkaAutoConfiguration 已在 application-it.yml 中排除；
    // AlarmService 通过构造器注入 KafkaTemplate，此处提供 Mock
    @MockBean
    @SuppressWarnings("rawtypes")
    KafkaTemplate kafkaTemplate;

    @MockBean
    StringRedisTemplate redisTemplate;

    @Autowired
    AlarmRecordMapper alarmRecordMapper;

    @Test
    @DisplayName("AlarmRecord — 所有 @TableField 列名映射正确（分区表 SELECT）")
    void alarmRecord_selectMappingCorrect() {
        assertThatNoException().isThrownBy(() ->
                alarmRecordMapper.selectOne(
                        new LambdaQueryWrapper<AlarmRecord>().last("LIMIT 1")));
    }

    @Test
    @DisplayName("AlarmRecord — INSERT 后 SELECT 验证关键字段（isSandbox、alarmLevel、confidence）")
    void alarmRecord_insertAndSelectRoundTrip() {
        AlarmRecord record = new AlarmRecord();
        record.setAlarmId("ALM-IT-" + System.currentTimeMillis());
        record.setSceneId("SCENE-IT-001");
        record.setFactoryCode("SINTER");
        record.setAlarmLevel("WARNING");
        record.setAnomalyType("集成测试异常");
        record.setConfidence(0.91);
        record.setImageUrl("minio://tianjing-frames-prod/sintering/SCENE-IT-001/test.jpg");
        record.setFrameId("f00001");
        record.setPluginId("ATOM-DETECT-YOLO-V1");
        record.setModelVersionId("MODEL-IT-001");
        record.setConfirmFrames(3);
        record.setIsSandbox(false);
        record.setPushStatus("PENDING");
        record.setAlarmAt(OffsetDateTime.now());

        assertThatNoException().isThrownBy(() -> alarmRecordMapper.insert(record));

        AlarmRecord loaded = alarmRecordMapper.selectById(record.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getAlarmId()).isEqualTo(record.getAlarmId());
        assertThat(loaded.getIsSandbox()).isFalse();
        assertThat(loaded.getConfidence()).isEqualTo(0.91);
    }
}
