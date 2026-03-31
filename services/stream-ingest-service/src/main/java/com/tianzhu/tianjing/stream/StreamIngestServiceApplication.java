package com.tianzhu.tianjing.stream;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 视频流接入服务
 *
 * 职责：拉取摄像头 RTSP 流，写入 tianjing.frame.production Topic
 * 规范：CLAUDE.md §4.2（stream-ingest-service）
 */
@SpringBootApplication(scanBasePackages = {
        "com.tianzhu.tianjing.stream",
        "com.tianzhu.tianjing.common"
})
public class StreamIngestServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(StreamIngestServiceApplication.class, args);
    }
}
