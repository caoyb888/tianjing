package com.tianzhu.tianjing.dashboard.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Sandbox PostgreSQL 主数据源配置（compare-dashboard-service 专用）
 * <p>
 * 问题背景：TDengineConfig 中声明了 DataSource Bean（tdengineDataSource），
 * 触发 Spring Boot 的 @ConditionalOnMissingBean(DataSource.class)，导致
 * spring.datasource 自动配置被跳过，MyBatis-Plus 误用 TDengine 作为主数据源。
 * <p>
 * 修复：此处显式声明 @Primary DataSource Bean，确保 MyBatis-Plus 始终使用
 * tianjing_sandbox PostgreSQL 库。
 * <p>
 * 规范：CLAUDE.md §7.1（PostgreSQL 规范，禁止跨库 JOIN）
 * 规范：CLAUDE.md §7.1（Sandbox 库与生产库严格隔离）
 */
@Configuration
public class SandboxDbConfig {

    @Value("${spring.datasource.url:jdbc:postgresql://localhost:5432/tianjing_sandbox}")
    private String url;

    @Value("${spring.datasource.username:tianjing_sandbox_app}")
    private String username;

    @Value("${spring.datasource.password:}")
    private String password;

    /**
     * @Primary：确保 MyBatis-Plus 使用此 PostgreSQL 数据源，
     * 而非 TDengineConfig 注册的 tdengineDataSource
     */
    @Primary
    @Bean("dataSource")
    public DataSource sandboxDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(20);
        config.setMinimumIdle(5);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setPoolName("SandboxPostgresPool");
        return new HikariDataSource(config);
    }
}
