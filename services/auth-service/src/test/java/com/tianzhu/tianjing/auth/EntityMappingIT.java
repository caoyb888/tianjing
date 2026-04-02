package com.tianzhu.tianjing.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianzhu.tianjing.auth.domain.SysUser;
import com.tianzhu.tianjing.auth.repository.SysUserMapper;
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
 * Entity 字段映射集成测试 — auth-service
 * 验证 SysUser 所有 @TableField 列名与 DB 对应。
 * 登录链路对字段完整性要求极高，任何字段映射错误直接导致 401。
 * 运行方式：mvn verify -Pintegration-test
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("it")
@Testcontainers
@DisplayName("Entity 字段映射集成测试 — auth-service")
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
    SysUserMapper sysUserMapper;

    @Test
    @DisplayName("SysUser — 所有 @TableField 列名映射正确")
    void sysUser_selectMappingCorrect() {
        assertThatNoException().isThrownBy(() ->
                sysUserMapper.selectOne(
                        new LambdaQueryWrapper<SysUser>().last("LIMIT 1")));
    }

    @Test
    @DisplayName("SysUser — INSERT 后 SELECT 验证登录关键字段完整回写")
    void sysUser_insertAndSelectRoundTrip() {
        SysUser user = new SysUser();
        user.setUserId("U-IT-" + System.currentTimeMillis());
        user.setUsername("it_test_user_" + System.currentTimeMillis());
        user.setPasswordHash("$2a$10$hashedpassword");
        user.setDisplayName("集成测试用户");
        user.setDeptCode("IT");
        user.setEmail("it@test.local");
        user.setStatus("ACTIVE");
        user.setIsLocked(false);
        user.setFailedLoginCount(0);
        user.setIsDeleted(false);
        user.setCreatedBy("it-test");
        user.setUpdatedBy("it-test");

        assertThatNoException().isThrownBy(() -> sysUserMapper.insert(user));

        SysUser loaded = sysUserMapper.selectById(user.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getUserId()).isEqualTo(user.getUserId());
        assertThat(loaded.getUsername()).isEqualTo(user.getUsername());
        assertThat(loaded.getPasswordHash()).isEqualTo(user.getPasswordHash());
        assertThat(loaded.getIsLocked()).isFalse();
        assertThat(loaded.getFailedLoginCount()).isZero();
    }

    @Test
    @DisplayName("SysUser — 按 username 查询（登录链路核心路径）")
    void sysUser_selectByUsername() {
        assertThatNoException().isThrownBy(() ->
                sysUserMapper.selectOne(
                        new LambdaQueryWrapper<SysUser>()
                                .eq(SysUser::getUsername, "nonexistent_user_for_it_test")));
    }
}
