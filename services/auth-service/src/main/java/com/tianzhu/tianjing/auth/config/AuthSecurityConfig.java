package com.tianzhu.tianjing.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.tianzhu.tianjing.common.config.CommonSecurityConfig;

@Configuration
@Import(CommonSecurityConfig.class)
public class AuthSecurityConfig {

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
