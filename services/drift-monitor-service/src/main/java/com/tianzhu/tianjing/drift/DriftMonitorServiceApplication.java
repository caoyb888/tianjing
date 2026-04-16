package com.tianzhu.tianjing.drift;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 模型漂移监测服务
 * 同时承载：§6.11 漂移 / §6.12 审计 / §6.10 训练管理 / §6.14 系统管理
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.tianzhu.tianjing.drift", "com.tianzhu.tianjing.common"})
public class DriftMonitorServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DriftMonitorServiceApplication.class, args);
    }
}
