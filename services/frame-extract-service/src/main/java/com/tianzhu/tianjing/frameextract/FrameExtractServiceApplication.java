package com.tianzhu.tianjing.frameextract;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 帧抽取服务
 *
 * 职责：按配置的帧率和 ROI 从视频流中抽取关键帧，输出至 Kafka
 * 规范：CLAUDE.md §4.2（frame-extract-service）
 */
@SpringBootApplication(scanBasePackages = {
        "com.tianzhu.tianjing.frameextract",
        "com.tianzhu.tianjing.common"
})
public class FrameExtractServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(FrameExtractServiceApplication.class, args);
    }
}
