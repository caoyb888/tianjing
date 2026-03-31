package com.tianzhu.tianjing.aggregate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 推理结果聚合服务
 *
 * 职责：消费 tianjing.infer.result.production/sandbox，写入 TDengine（is_sandbox 标签必须正确标记）
 * 安全约束：写入 TDengine 时必须正确设置 is_sandbox 标签（规范：CLAUDE.md §7.2，§15 P2）
 * 规范：CLAUDE.md §4.2（result-aggregate-service）
 */
@SpringBootApplication(scanBasePackages = {
        "com.tianzhu.tianjing.aggregate",
        "com.tianzhu.tianjing.common"
})
public class ResultAggregateServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ResultAggregateServiceApplication.class, args);
    }
}
