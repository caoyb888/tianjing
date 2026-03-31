package com.tianzhu.tianjing.scene;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.tianzhu.tianjing.scene", "com.tianzhu.tianjing.common"})
public class SceneConfigServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SceneConfigServiceApplication.class, args);
    }
}
