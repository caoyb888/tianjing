package com.tianzhu.tianjing.dashboard.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * TDengine 数据源配置（大屏统计查询）
 * 规范：CLAUDE.md §7.2（TDengine 3.x、时序数据存储）
 * 使用 WebSocket JDBC 驱动，无需本地 native 库
 */
@Configuration
public class TDengineConfig {

    @Value("${tianjing.tdengine.url:jdbc:TAOS-RS://localhost:6041/tianjing_ts?timezone=UTC}")
    private String url;

    @Value("${tianjing.tdengine.username:root}")
    private String username;

    @Value("${tianjing.tdengine.password:taosdata}")
    private String password;

    @Value("${tianjing.tdengine.driver-class-name:com.taosdata.jdbc.rs.RestfulDriver}")
    private String driverClassName;

    @Bean("tdengineDataSource")
    public DataSource tdengineDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClassName);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(5000);
        config.setPoolName("DashboardTDenginePool");
        // -1：启动时不验证连接，TDengine 不可用时服务仍可启动，查询降级返回 0
        config.setInitializationFailTimeout(-1);
        return new HikariDataSource(config);
    }

    @Bean("tdengineJdbcTemplate")
    public JdbcTemplate tdengineJdbcTemplate() {
        return new JdbcTemplate(tdengineDataSource());
    }
}
