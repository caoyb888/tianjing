package com.tianzhu.tianjing.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianzhu.tianjing.auth.domain.SysUser;
import com.tianzhu.tianjing.auth.dto.LoginRequest;
import com.tianzhu.tianjing.auth.dto.LoginResponse;
import com.tianzhu.tianjing.auth.dto.UserInfoResponse;
import com.tianzhu.tianjing.auth.repository.SysUserMapper;
import com.tianzhu.tianjing.common.exception.BusinessException;
import com.tianzhu.tianjing.common.exception.ErrorCode;
import com.tianzhu.tianjing.common.security.JwtProperties;
import com.tianzhu.tianjing.common.security.JwtTokenProvider;
import com.tianzhu.tianjing.common.security.TianjingUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AuthService 单元测试
 * 覆盖：login / getCurrentUser / logout / refresh / 连续登录失败锁定
 * 规范：CLAUDE.md §12.1，要求 Service 层覆盖率 ≥ 80%
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService — 鉴权业务服务单元测试")
class AuthServiceTest {

    @Mock private SysUserMapper userMapper;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private JwtProperties jwtProperties;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private BCryptPasswordEncoder passwordEncoder;
    private AuthService authService;

    private static final String RAW_PASSWORD = "Test@1234";
    private static final String TOKEN = "eyJhbGciOiJSUzI1NiJ9.test";

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(userMapper, jwtTokenProvider, jwtProperties, redisTemplate, passwordEncoder);
    }

    // ========== login() ==========

    @Test
    @DisplayName("login — 正确用户名密码，签发 access_token")
    void login_validCredentials_returnsToken() {
        SysUser user = buildUser("ACTIVE");
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(userMapper.selectRolesByUserId(user.getId())).thenReturn(List.of("OPERATOR"));
        when(userMapper.updateById(any())).thenReturn(1);
        when(jwtTokenProvider.generateAccessToken(anyString(), anyLong(), anyList())).thenReturn(TOKEN);
        when(jwtProperties.getAccessTokenExpireSeconds()).thenReturn(3600L);

        LoginResponse resp = authService.login(new LoginRequest("testuser", RAW_PASSWORD));

        assertThat(resp.accessToken()).isEqualTo(TOKEN);
        assertThat(resp.user().username()).isEqualTo("testuser");
        assertThat(resp.user().roles()).contains("OPERATOR");
        verify(userMapper).updateById(argThat(u -> ((SysUser) u).getFailedLoginCount() == 0));
    }

    @Test
    @DisplayName("login — 用户不存在，抛出 CREDENTIALS_INVALID")
    void login_userNotFound_throwsCredentialsInvalid() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> authService.login(new LoginRequest("ghost", "pwd")))
                .satisfies(e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CREDENTIALS_INVALID));

        verify(jwtTokenProvider, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    @DisplayName("login — 密码错误，抛出 CREDENTIALS_INVALID 并递增失败计数")
    void login_wrongPassword_incrementsFailedCount() {
        SysUser user = buildUser("ACTIVE");
        user.setFailedLoginCount(0);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(userMapper.updateById(any())).thenReturn(1);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> authService.login(new LoginRequest("testuser", "wrongpass")))
                .satisfies(e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CREDENTIALS_INVALID));

        verify(userMapper).updateById(argThat(u -> ((SysUser) u).getFailedLoginCount() == 1));
    }

    @Test
    @DisplayName("login — 账号已锁定，抛出 ACCOUNT_LOCKED")
    void login_accountLocked_throwsAccountLocked() {
        SysUser user = buildUser("LOCKED");
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> authService.login(new LoginRequest("testuser", RAW_PASSWORD)))
                .satisfies(e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED));
    }

    @Test
    @DisplayName("login — 连续 5 次密码错误自动锁定账号")
    void login_fiveConsecutiveFailures_locksAccount() {
        SysUser user = buildUser("ACTIVE");
        user.setFailedLoginCount(4); // 第 5 次失败触发锁定
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(userMapper.updateById(any())).thenReturn(1);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> authService.login(new LoginRequest("testuser", "wrongpass")));

        verify(userMapper).updateById(argThat(u -> "LOCKED".equals(((SysUser) u).getStatus())));
    }

    // ========== getCurrentUser() ==========

    @Test
    @DisplayName("getCurrentUser — 正常返回用户信息（含 email）")
    void getCurrentUser_existingUser_returnsUserInfo() {
        SysUser user = buildUser("ACTIVE");
        user.setEmail("test@tianzhu.com");
        TianjingUserDetails principal = new TianjingUserDetails("testuser", 1L, List.of("ADMIN"), List.of());

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(userMapper.selectRolesByUserId(user.getId())).thenReturn(List.of("ADMIN", "OPERATOR"));

        UserInfoResponse info = authService.getCurrentUser(principal);

        assertThat(info.username()).isEqualTo("testuser");
        assertThat(info.email()).isEqualTo("test@tianzhu.com");
        assertThat(info.roles()).containsExactlyInAnyOrder("ADMIN", "OPERATOR");
    }

    @Test
    @DisplayName("getCurrentUser — 用户已删除，抛出 USER_NOT_FOUND")
    void getCurrentUser_deletedUser_throwsUserNotFound() {
        TianjingUserDetails principal = new TianjingUserDetails("ghost", 99L, List.of(), List.of());
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> authService.getCurrentUser(principal))
                .satisfies(e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    // ========== logout() ==========

    @Test
    @DisplayName("logout — 有效 Token 加入 Redis 黑名单")
    void logout_validToken_addedToBlacklist() {
        io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
        when(claims.getId()).thenReturn("jti-abc123");
        when(claims.getExpiration()).thenReturn(new java.util.Date(System.currentTimeMillis() + 60_000));
        when(jwtTokenProvider.parseToken(TOKEN)).thenReturn(claims);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        authService.logout(TOKEN);

        verify(valueOps).set(eq("tianjing:token:blacklist:jti-abc123"), eq("1"), any(java.time.Duration.class));
    }

    @Test
    @DisplayName("logout — Token 已过期（parseToken 抛异常），静默处理不报错")
    void logout_expiredToken_noException() {
        when(jwtTokenProvider.parseToken(TOKEN)).thenThrow(new RuntimeException("expired"));

        assertThatNoException().isThrownBy(() -> authService.logout(TOKEN));
    }

    // ========== refresh() ==========

    @Test
    @DisplayName("refresh — 旧 Token 加黑名单，签发新 Token")
    void refresh_validUser_invalidatesOldAndIssuesNew() {
        SysUser user = buildUser("ACTIVE");
        TianjingUserDetails principal = new TianjingUserDetails("testuser", 1L, List.of("OPERATOR"), List.of());

        // logout 行为：parseToken 成功
        io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
        when(claims.getId()).thenReturn("old-jti");
        when(claims.getExpiration()).thenReturn(new java.util.Date(System.currentTimeMillis() + 60_000));
        when(jwtTokenProvider.parseToken("old-token")).thenReturn(claims);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(userMapper.selectRolesByUserId(user.getId())).thenReturn(List.of("OPERATOR"));
        when(jwtTokenProvider.generateAccessToken(anyString(), anyLong(), anyList())).thenReturn("new-token");
        when(jwtProperties.getAccessTokenExpireSeconds()).thenReturn(3600L);

        LoginResponse resp = authService.refresh(principal, "old-token");

        assertThat(resp.accessToken()).isEqualTo("new-token");
        verify(valueOps).set(contains("old-jti"), eq("1"), any());
    }

    @Test
    @DisplayName("refresh — 用户不存在，抛出 USER_NOT_FOUND")
    void refresh_userNotFound_throwsException() {
        TianjingUserDetails principal = new TianjingUserDetails("ghost", 99L, List.of(), List.of());

        io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
        when(claims.getId()).thenReturn("jti-x");
        when(claims.getExpiration()).thenReturn(new java.util.Date(System.currentTimeMillis() + 10_000));
        when(jwtTokenProvider.parseToken(TOKEN)).thenReturn(claims);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> authService.refresh(principal, TOKEN))
                .satisfies(e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
    }

    // ========== 工厂方法 ==========

    private SysUser buildUser(String status) {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUserId("USR-001");
        user.setUsername("testuser");
        user.setPasswordHash(passwordEncoder.encode(RAW_PASSWORD));
        user.setDisplayName("测试用户");
        user.setStatus(status);
        user.setFailedLoginCount(0);
        user.setIsDeleted(0);
        user.setCreatedAt(OffsetDateTime.now());
        return user;
    }
}
