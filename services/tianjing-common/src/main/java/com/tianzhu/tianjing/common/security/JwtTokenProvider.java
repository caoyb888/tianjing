package com.tianzhu.tianjing.common.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

/**
 * JWT 令牌工具类（RS256 算法）
 * 规范：CLAUDE.md §10.3，API 接口规范 V3.1 §2.1
 * 禁止使用 HS256。
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final JwtProperties properties;
    private PrivateKey privateKey;
    private PublicKey publicKey;

    @Autowired
    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        initKeys();
    }

    private void initKeys() {
        try {
            if (properties.getPrivateKeyPem() != null && !properties.getPrivateKeyPem().isBlank()) {
                this.privateKey = loadPrivateKey(properties.getPrivateKeyPem());
            }
            if (properties.getPublicKeyPem() != null && !properties.getPublicKeyPem().isBlank()) {
                this.publicKey = loadPublicKey(properties.getPublicKeyPem());
            }
        } catch (Exception e) {
            log.warn("JWT 密钥加载失败（开发模式将降级运行）: {}", e.getMessage());
        }
    }

    /**
     * 签发 access_token
     * Claims 包含：sub（用户名）、userId、roles、tokenType
     */
    public String generateAccessToken(String username, Long userId, List<String> roles) {
        if (privateKey == null) {
            throw new IllegalStateException("RSA 私钥未配置，无法签发 JWT");
        }
        Date now = new Date();
        Date expiry = new Date(now.getTime() + properties.getAccessTokenExpireSeconds() * 1000L);

        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("roles", roles)
                .claim("tokenType", "access")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    /**
     * 签发 refresh_token（Claims 精简，仅含 sub + tokenType）
     */
    public String generateRefreshToken(String username) {
        if (privateKey == null) {
            throw new IllegalStateException("RSA 私钥未配置，无法签发 JWT");
        }
        Date now = new Date();
        Date expiry = new Date(now.getTime() + properties.getRefreshTokenExpireSeconds() * 1000L);

        return Jwts.builder()
                .subject(username)
                .claim("tokenType", "refresh")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    /**
     * 验证并解析 Token，返回 Claims
     * 无效 Token 抛出 JwtException
     */
    public Claims parseToken(String token) {
        if (publicKey == null) {
            throw new IllegalStateException("RSA 公钥未配置，无法验证 JWT");
        }
        try {
            return Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException | SignatureException | MalformedJwtException | UnsupportedJwtException e) {
            log.debug("JWT 解析失败: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 从 Token 中提取用户名（不验证签名，用于注销场景）
     */
    public String extractUsername(String token) {
        try {
            return parseToken(token).getSubject();
        } catch (JwtException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(Claims claims) {
        Object rolesObj = claims.get("roles");
        if (rolesObj instanceof List<?>) {
            return (List<String>) rolesObj;
        }
        return Collections.emptyList();
    }

    // ==============================
    // PEM 密钥加载（BouncyCastle）
    // ==============================

    private PrivateKey loadPrivateKey(String pem) throws Exception {
        try (PemReader reader = new PemReader(new StringReader(pem))) {
            PemObject obj = reader.readPemObject();
            byte[] content = obj.getContent();
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(content);
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        }
    }

    private PublicKey loadPublicKey(String pem) throws Exception {
        try (PemReader reader = new PemReader(new StringReader(pem))) {
            PemObject obj = reader.readPemObject();
            byte[] content = obj.getContent();
            X509EncodedKeySpec spec = new X509EncodedKeySpec(content);
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        }
    }
}
