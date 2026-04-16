package com.tianzhu.tianjing.calibration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.tianzhu.tianjing.calibration", "com.tianzhu.tianjing.common"})
public class CalibrationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CalibrationServiceApplication.class, args);
    }
}
