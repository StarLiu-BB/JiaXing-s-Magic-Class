package com.zhixue.media.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.MinioProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端配置。
 */
@Configuration
@EnableConfigurationProperties(MinioProperties.class)
@RequiredArgsConstructor
public class MinioConfig {

    private final MinioProperties properties;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }

    @Bean
    public MinioInitializer minioInitializer(MinioClient client) {
        return new MinioInitializer(client, properties);
    }

    /**
     * 属性绑定。
     */
    @Data
    @ConfigurationProperties(prefix = "minio")
    public static class MinioProperties {
        /**
         * MinIO 访问地址。
         */
        private String endpoint;
        /**
         * 控制台外网访问地址，用于拼接下载 URL。
         */
        private String externalUrl;
        /**
         * 访问 Key。
         */
        private String accessKey;
        /**
         * 访问 Secret。
         */
        private String secretKey;
        /**
         * 默认桶名称。
         */
        private String bucket;
        /**
         * 分片在桶中的存储前缀。
         */
        private String chunkPath = "chunks";
    }

    /**
     * 在启动时确保桶存在。
     */
    public static class MinioInitializer {
        public MinioInitializer(MinioClient client, MinioProperties properties) {
            try {
                boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(properties.getBucket()).build());
                if (!exists) {
                    client.makeBucket(MakeBucketArgs.builder().bucket(properties.getBucket()).build());
                }
            } catch (Exception e) {
                throw new IllegalStateException("初始化 MinIO 桶失败", e);
            }
        }
    }
}

