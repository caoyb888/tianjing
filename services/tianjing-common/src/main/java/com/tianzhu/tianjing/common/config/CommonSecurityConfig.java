package com.tianzhu.tianjing.common.config;

import com.tianzhu.tianjing.common.exception.ErrorCode;
import com.tianzhu.tianjing.common.response.ApiResponse;
import com.tianzhu.tianjing.common.security.JwtAuthenticationFilter;
import com.tianzhu.tianjing.common.security.JwtProperties;
import com.tianzhu.tianjing.common.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.nio.charset.StandardCharsets;

/**
 * 通用 Spring Security 配置基类
 * 各服务可继承或直接导入此配置。
 * 内部服务接口（/internal/**）通过 NetworkPolicy + mTLS 保护，不走 JWT。
 */
@EnableConfigurationProperties(JwtProperties.class)
@EnableMethodSecurity
@RequiredArgsConstructor
public class CommonSecurityConfig {

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
                // 内部服务接口：通过 K8s NetworkPolicy + mTLS 保护，不走 JWT
                .requestMatchers("/internal/**").permitAll()
                // Actuator 健康检查
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // 登录接口本身不需要认证
                .requestMatchers("/api/v1/auth/login").permitAll()
                // 其余所有接口都需要认证
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                            ApiResponse.error(ErrorCode.TOKEN_MISSING_OR_EXPIRED));
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    writeError(response, HttpServletResponse.SC_FORBIDDEN,
                            ApiResponse.error(ErrorCode.PERMISSION_DENIED));
                })
            )
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeError(HttpServletResponse response, int status, ApiResponse<?> body) {
        try {
            response.setStatus(status);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(objectMapper.writeValueAsString(body));
        } catch (Exception ignored) {}
    }
}
