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
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 鉴权业务服务
 * 规范：API 接口规范 V3.1 §6.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String BLACKLIST_PREFIX = "tianjing:token:blacklist:";
    private static final int MAX_FAILED_LOGIN = 5;

    private final SysUserMapper userMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final StringRedisTemplate redisTemplate;
    private final BCryptPasswordEncoder passwordEncoder;

    /**
     * 用户登录
     * 1. 校验用户名/密码
     * 2. 检查账号锁定状态
     * 3. 签发 access_token
     * 4. 更新登录时间、重置失败计数
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.username())
                );

        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            if (user != null) {
                incrementFailedLogin(user);
            }
            throw BusinessException.of(ErrorCode.CREDENTIALS_INVALID);
        }

        if ("LOCKED".equals(user.getStatus())) {
            throw BusinessException.of(ErrorCode.ACCOUNT_LOCKED);
        }

        List<String> roles = userMapper.selectRolesByUserId(user.getId());
        user.setRoles(roles);

        // 重置失败计数 + 更新登录时间
        user.setFailedLoginCount(0);
        user.setLastLoginAt(OffsetDateTime.now());
        userMapper.updateById(user);

        String token = jwtTokenProvider.generateAccessToken(user.getUsername(), user.getId(), roles);
        log.info("用户登录成功 username={} roles={}", user.getUsername(), roles);

        return LoginResponse.of(token, jwtProperties.getAccessTokenExpireSeconds(), user);
    }

    /**
     * 刷新 Token
     * 将当前 Token 加入黑名单并签发新 Token
     */
    public LoginResponse refresh(TianjingUserDetails currentUser, String oldToken) {
        // 将旧 Token 加入黑名单
        invalidateToken(oldToken);

        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, currentUser.getUsername())
                );

        if (user == null) {
            throw BusinessException.of(ErrorCode.USER_NOT_FOUND);
        }

        List<String> roles = userMapper.selectRolesByUserId(user.getId());
        user.setRoles(roles);

        String newToken = jwtTokenProvider.generateAccessToken(user.getUsername(), user.getId(), roles);
        return LoginResponse.of(newToken, jwtProperties.getAccessTokenExpireSeconds(), user);
    }

    /**
     * 获取当前登录用户信息
     * GET /auth/me — 从 DB 补全 displayName、email 等字段
     */
    public UserInfoResponse getCurrentUser(TianjingUserDetails currentUser) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, currentUser.getUsername())
                );

        if (user == null) {
            throw BusinessException.of(ErrorCode.USER_NOT_FOUND);
        }

        List<String> roles = userMapper.selectRolesByUserId(user.getId());
        return UserInfoResponse.of(user, roles);
    }

    /**
     * 退出登录
     * 将当前 Token 加入 Redis 黑名单（TTL = Token 剩余有效期）
     */
    public void logout(String token) {
        invalidateToken(token);
        log.info("用户已退出登录");
    }

    private void invalidateToken(String token) {
        try {
            Claims claims = jwtTokenProvider.parseToken(token);
            String jti = claims.getId();
            if (jti == null || jti.isBlank()) {
                // Token 没有 jti，用 token hash 作为 key
                jti = String.valueOf(token.hashCode());
            }
            long remainingMs = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (remainingMs > 0) {
                redisTemplate.opsForValue().set(
                        BLACKLIST_PREFIX + jti,
                        "1",
                        Duration.ofMillis(remainingMs)
                );
            }
        } catch (Exception e) {
            log.debug("Token 已失效，无需加入黑名单: {}", e.getMessage());
        }
    }

    private void incrementFailedLogin(SysUser user) {
        int count = (user.getFailedLoginCount() == null ? 0 : user.getFailedLoginCount()) + 1;
        user.setFailedLoginCount(count);
        if (count >= MAX_FAILED_LOGIN) {
            user.setStatus("LOCKED");
            log.warn("账号 {} 连续登录失败 {} 次，已锁定", user.getUsername(), count);
        }
        userMapper.updateById(user);
    }
}
