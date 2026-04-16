package com.tianzhu.tianjing.device;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianzhu.tianjing.device.domain.CameraDevice;
import com.tianzhu.tianjing.device.domain.CameraHealthRecord;
import com.tianzhu.tianjing.device.repository.CameraDeviceMapper;
import com.tianzhu.tianjing.device.repository.CameraHealthRecordMapper;
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
 * Entity 字段映射集成测试 — device-manage-service
 * 验证 CameraDevice、CameraHealthRecord 所有 @TableField 列名与 DB 对应。
 * 运行方式：mvn verify -Pintegration-test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("it")
@Testcontainers
@DisplayName("Entity 字段映射集成测试 — device-manage-service")
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
    CameraDeviceMapper cameraDeviceMapper;

    @Autowired
    CameraHealthRecordMapper cameraHealthRecordMapper;

    @Test
    @DisplayName("CameraDevice — 所有 @TableField 列名映射正确")
    void cameraDevice_selectMappingCorrect() {
        assertThatNoException().isThrownBy(() ->
                cameraDeviceMapper.selectOne(
                        new LambdaQueryWrapper<CameraDevice>().last("LIMIT 1")));
    }

    @Test
    @DisplayName("CameraDevice — INSERT 后 SELECT 验证字段完整回写")
    void cameraDevice_insertAndSelectRoundTrip() {
        CameraDevice device = new CameraDevice();
        device.setDeviceCode("CAM-IT-" + System.currentTimeMillis());
        device.setDeviceName("集成测试摄像头");
        device.setSceneId("SCENE-IT-001");
        device.setIpAddress("192.168.0.99");
        device.setProtocol("RTSP");
        device.setResolutionWidth(1920);
        device.setResolutionHeight(1080);
        device.setFps(25);
        device.setHealthStatus("NORMAL");
        device.setIsDeleted(false);
        device.setCreatedBy("it-test");
        device.setUpdatedBy("it-test");

        assertThatNoException().isThrownBy(() -> cameraDeviceMapper.insert(device));

        CameraDevice loaded = cameraDeviceMapper.selectById(device.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getDeviceCode()).isEqualTo(device.getDeviceCode());
        assertThat(loaded.getResolutionWidth()).isEqualTo(1920);
        assertThat(loaded.getHealthStatus()).isEqualTo("NORMAL");
    }

    @Test
    @DisplayName("CameraHealthRecord — 所有 @TableField 列名映射正确（分区表）")
    void cameraHealthRecord_selectMappingCorrect() {
        // camera_health_record 是分区表，SELECT 验证列名映射
        assertThatNoException().isThrownBy(() ->
                cameraHealthRecordMapper.selectOne(
                        new LambdaQueryWrapper<CameraHealthRecord>().last("LIMIT 1")));
    }

    @Test
    @DisplayName("CameraHealthRecord — INSERT 到分区表字段均正确写入")
    void cameraHealthRecord_insertMappingCorrect() {
        CameraHealthRecord record = new CameraHealthRecord();
        record.setDeviceCode("CAM-IT-001");
        record.setSceneId("SCENE-IT-001");
        record.setCheckAt(OffsetDateTime.now());
        record.setLaplacianVar(120.5);
        record.setAvgBrightness(85.0);
        record.setOpticalFlowDx(0.1);
        record.setOpticalFlowDy(0.1);
        record.setHealthScore(95);
        record.setFaultType(null);

        assertThatNoException().isThrownBy(() -> cameraHealthRecordMapper.insert(record));
    }
}
