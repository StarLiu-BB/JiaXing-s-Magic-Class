package com.zhixue.ai.config;

import ai.z.openapi.ZhipuAiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 智谱 AI 配置类。
 * 负责创建和配置 ZhipuAiClient Bean，供其他服务注入使用。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(ZhipuAiProperties.class)
public class ZhipuAiConfig {

    private final ZhipuAiProperties properties;

    /**
     * 创建智谱 AI 客户端 Bean。
     * 只有当 zhipuai.enabled=true 时才会创建（默认启用）。
     *
     * @return ZhipuAiClient 实例
     */
    @Bean
    @ConditionalOnProperty(prefix = "zhipuai", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ZhipuAiClient zhipuAiClient() {
        log.info("初始化智谱 AI 客户端，使用模型: {}", properties.getModel());
        
        return ZhipuAiClient.builder()
                .ofZHIPU()
                .apiKey(properties.getApiKey())
                .build();
    }
}
