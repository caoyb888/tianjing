package com.tianzhu.tianjing.algomodel.config;
import com.tianzhu.tianjing.common.config.CommonSecurityConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
@Configuration
@Import(CommonSecurityConfig.class)
public class AlgoModelSecurityConfig {}
