package com.tianzhu.tianjing.algomodel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 算法插件注册服务 + 模型版本管理服务
 * 规范：API 接口规范 V3.1 §6.5（算法管理 3 个接口）+ §6.6（模型管理 5 个接口）
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.tianzhu.tianjing.algomodel", "com.tianzhu.tianjing.common"})
public class AlgoModelServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AlgoModelServiceApplication.class, args);
    }
}
