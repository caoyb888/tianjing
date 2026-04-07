package com.tianzhu.tianjing.dashboard.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * tianjing_prod 生产库第二数据源（只读统计查询）
 * 不暴露 DataSource Bean，避免 MyBatis-Plus 误用。
 * DashboardController 通过 @Qualifier("prodJdbcTemplate") 注入。
 */
@Configuration
public class ProdDbConfig {

    @Value("${tianjing.prod-db.url:jdbc:postgresql://localhost:5432/tianjing_prod}")
    private String url;

    @Value("${tianjing.prod-db.username:tianjing_prod_user}")
    private String username;

    @Value("${tianjing.prod-db.password:}")
    private String password;

    @Bean("prodJdbcTemplate")
    public JdbcTemplate prodJdbcTemplate() {
        return new JdbcTemplate(
                DataSourceBuilder.create()
                        .url(url)
                        .username(username)
                        .password(password)
                        .driverClassName("org.postgresql.Driver")
                        .build()
        );
    }
}
