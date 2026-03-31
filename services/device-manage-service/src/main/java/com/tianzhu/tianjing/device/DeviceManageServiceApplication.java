package com.tianzhu.tianjing.device;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.tianzhu.tianjing.device", "com.tianzhu.tianjing.common"})
public class DeviceManageServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(DeviceManageServiceApplication.class, args);
    }
}
