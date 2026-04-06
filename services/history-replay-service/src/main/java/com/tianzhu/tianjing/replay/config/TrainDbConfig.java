package com.tianzhu.tianjing.replay.config;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * tianjing_train 训练库第二数据源（仅 JdbcTemplate，不暴露 DataSource Bean）
 * 不注册 DataSource Bean，避免 MyBatis-Plus 误用此数据源替代主库。
 * DatasetExportService 通过 @Qualifier("trainJdbcTemplate") 注入使用。
 */
@Configuration
public class TrainDbConfig {

    @Value("${tianjing.train-db.url:jdbc:postgresql://localhost:5432/tianjing_train}")
    private String url;

    @Value("${tianjing.train-db.username:tianjing_train_user}")
    private String username;

    @Value("${tianjing.train-db.password:}")
    private String password;

    @Bean("trainJdbcTemplate")
    public JdbcTemplate trainJdbcTemplate() {
        // DataSource 不作为独立 Bean 暴露，直接内嵌，防止 MyBatis-Plus 误用
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
