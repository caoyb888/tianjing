package com.tianzhu.tianjing.calibration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianzhu.tianjing.calibration.domain.CalibrationRecord;
import com.tianzhu.tianjing.calibration.repository.CalibrationMapper;
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

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Entity 字段映射集成测试 — calibration-service
 * 验证 CalibrationRecord 所有 @TableField 列名与 DB 对应。
 * 运行方式：mvn verify -Pintegration-test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("it")
@Testcontainers
@DisplayName("Entity 字段映射集成测试 — calibration-service")
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
    CalibrationMapper calibrationMapper;

    @Test
    @DisplayName("CalibrationRecord — 所有 @TableField 列名映射正确")
    void calibrationRecord_selectMappingCorrect() {
        assertThatNoException().isThrownBy(() ->
                calibrationMapper.selectOne(
                        new LambdaQueryWrapper<CalibrationRecord>().last("LIMIT 1")));
    }

    @Test
    @DisplayName("CalibrationRecord — INSERT 后 SELECT 验证字段完整回写")
    void calibrationRecord_insertAndSelectRoundTrip() {
        CalibrationRecord record = new CalibrationRecord();
        record.setCalibrationId("CAL-IT-" + System.currentTimeMillis());
        record.setSceneId("SCENE-IT-001");
        record.setDeviceCode("CAM-IT-001");
        record.setRefDistanceMm(new BigDecimal("300.000"));
        record.setPixelP1X(100);
        record.setPixelP1Y(200);
        record.setPixelP2X(400);
        record.setPixelP2Y(200);
        record.setPixelDistance(300);
        record.setScaleMmPerPx(new BigDecimal("1.000000"));
        record.setIsActive(true);
        record.setCalibratedBy("it-test");
        record.setCreatedBy("it-test");
        record.setUpdatedBy("it-test");

        assertThatNoException().isThrownBy(() -> calibrationMapper.insert(record));

        CalibrationRecord loaded = calibrationMapper.selectById(record.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getCalibrationId()).isEqualTo(record.getCalibrationId());
        assertThat(loaded.getIsActive()).isTrue();
        assertThat(loaded.getScaleMmPerPx()).isEqualByComparingTo(new BigDecimal("0.067"));
    }
}
