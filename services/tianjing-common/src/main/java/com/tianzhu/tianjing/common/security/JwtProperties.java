package com.tianzhu.tianjing.common.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置属性
 * 密钥从 Nacos / K8s Secret 注入，禁止硬编码
 */
@Data
@ConfigurationProperties(prefix = "tianjing.jwt")
public class JwtProperties {

    /** RSA 私钥（PEM 格式，仅 auth-service 使用）*/
    private String privateKeyPem;

    /** RSA 公钥（PEM 格式，所有服务共享）*/
    private String publicKeyPem;

    /** access_token 过期时间（秒），默认 8 小时 */
    private long accessTokenExpireSeconds = 8 * 3600L;

    /** refresh_token 过期时间（秒），默认 7 天 */
    private long refreshTokenExpireSeconds = 7 * 24 * 3600L;
}
