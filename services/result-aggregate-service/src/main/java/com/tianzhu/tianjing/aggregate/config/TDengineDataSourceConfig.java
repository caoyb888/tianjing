package com.tianzhu.tianjing.aggregate.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * TDengine 数据源配置
 * 使用 HikariCP 连接池 + TDengine WebSocket JDBC 驱动
 * 规范：CLAUDE.md §7.2（TDengine 3.x、时序数据存储）
 */
@Configuration
public class TDengineDataSourceConfig {

    @Value("${spring.datasource.tdengine.url}")
    private String url;
    @Value("${spring.datasource.tdengine.username}")
    private String username;
    @Value("${spring.datasource.tdengine.password}")
    private String password;
    @Value("${spring.datasource.tdengine.driver-class-name}")
    private String driverClassName;

    @Bean(name = "tdengineDataSource")
    public DataSource tdengineDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClassName);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(5000);
        config.setPoolName("TDengineHikariPool");
        return new HikariDataSource(config);
    }
}
