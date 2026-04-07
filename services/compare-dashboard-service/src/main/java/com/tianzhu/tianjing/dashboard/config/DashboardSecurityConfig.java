package com.tianzhu.tianjing.dashboard.config;

import com.tianzhu.tianjing.common.security.JwtAuthenticationFilter;
import com.tianzhu.tianjing.common.security.JwtProperties;
import com.tianzhu.tianjing.common.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Dashboard 服务安全配置
 * 覆盖 CommonSecurityConfig，对大屏监控只读接口开放免认证访问。
 * 大屏（monitor-screen）为无登录公开展示屏，GET /api/v1/dashboard/** 免认证。
 * 其余写操作接口仍需 JWT 认证。
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
@EnableMethodSecurity
@RequiredArgsConstructor
public class DashboardSecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenProvider, redisTemplate);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 内部接口：K8s NetworkPolicy 保护，免 JWT
                .requestMatchers("/internal/**").permitAll()
                // Actuator 健康检查
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // 大屏监控只读接口：monitor-screen 无登录公开展示，免认证
                // SECURITY: 仅开放 GET 方法；接口返回只读统计数据，无敏感信息
                .requestMatchers(HttpMethod.GET, "/api/v1/dashboard/**").permitAll()
                // 其余接口仍需认证（Sandbox 管理等写操作）
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
