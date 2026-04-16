package com.tianzhu.tianjing.replay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.tianzhu.tianjing.replay", "com.tianzhu.tianjing.common"})
public class HistoryReplayServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(HistoryReplayServiceApplication.class, args);
    }
}
