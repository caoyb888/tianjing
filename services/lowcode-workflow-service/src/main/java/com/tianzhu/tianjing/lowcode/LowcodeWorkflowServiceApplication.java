package com.tianzhu.tianjing.lowcode;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 低代码编排服务 — Sprint S1-21
 *
 * 职责：7种节点类型的工作流画布后端，生成 workflow_json，下发至路由服务
 * 规范：CLAUDE.md §4.2（lowcode-workflow-service），禁止直接操作推理进程
 */
@SpringBootApplication(scanBasePackages = {
        "com.tianzhu.tianjing.lowcode",
        "com.tianzhu.tianjing.common"
})
public class LowcodeWorkflowServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(LowcodeWorkflowServiceApplication.class, args);
    }
}
