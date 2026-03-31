package com.tianzhu.tianjing.preprocess;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 图像预处理服务
 *
 * 职责：对抽取的帧执行去雾、增强、ROI 裁剪等预处理，输出至 route-dispatch-service
 * 规范：CLAUDE.md §4.2（preprocess-service）
 */
@SpringBootApplication(scanBasePackages = {
        "com.tianzhu.tianjing.preprocess",
        "com.tianzhu.tianjing.common"
})
public class PreprocessServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PreprocessServiceApplication.class, args);
    }
}
