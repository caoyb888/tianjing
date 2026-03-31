package com.tianzhu.tianjing.route;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 路由分发服务 — Sprint S1-02
 *
 * 职责：
 *   1. 消费 tianjing.frame.production 帧数据
 *   2. 从 Redis 读取 scene 路由配置（tianjing:scene:active:{scene_id}），热更新 <1s
 *   3. 将帧分发至对应推理 Topic（或 sandbox topic）
 *   4. 提供内部管理接口：强制刷新路由缓存
 *
 * 规范：CLAUDE.md §4.2、§8.1、§8.2
 */
@SpringBootApplication(scanBasePackages = {
        "com.tianzhu.tianjing.route",
        "com.tianzhu.tianjing.common"
})
@EnableScheduling
public class RouteDispatchServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(RouteDispatchServiceApplication.class, args);
    }
}
