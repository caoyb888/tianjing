package com.tianzhu.tianjing.health;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.tianzhu.tianjing.health", "com.tianzhu.tianjing.common"})
public class HealthMonitorServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(HealthMonitorServiceApplication.class, args);
    }
}
