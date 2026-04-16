package com.tianzhu.tianjing.replay.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端配置
 * 用于离线仿真视频文件上传（tianjing-sim-temp bucket）
 */
@Configuration
public class MinioConfig {

    @Value("${tianjing.minio.endpoint:http://localhost:9000}")
    private String endpoint;

    @Value("${tianjing.minio.access-key:minioadmin}")
    private String accessKey;

    @Value("${tianjing.minio.secret-key:minioadmin123}")
    private String secretKey;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
