package com.tianzhu.tianjing.mirror;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * T型分流镜像服务
 *
 * 职责：
 *   - 消费 tianjing.frame.production，按 1:5 降频复制至 tianjing.frame.sandbox
 *   - 复制时设置 is_sandbox=true，不得修改原始帧数据
 *   - 绝不影响生产 Topic 的正常消费
 *
 * 规范：CLAUDE.md §4.2（traffic-mirror-service），禁止修改原始帧数据
 */
@SpringBootApplication(scanBasePackages = {
        "com.tianzhu.tianjing.mirror",
        "com.tianzhu.tianjing.common"
})
public class TrafficMirrorServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TrafficMirrorServiceApplication.class, args);
    }
}
