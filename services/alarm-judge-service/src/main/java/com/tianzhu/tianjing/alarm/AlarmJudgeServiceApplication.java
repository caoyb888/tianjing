package com.tianzhu.tianjing.alarm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.tianzhu.tianjing.alarm", "com.tianzhu.tianjing.common"})
public class AlarmJudgeServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AlarmJudgeServiceApplication.class, args);
    }
}
