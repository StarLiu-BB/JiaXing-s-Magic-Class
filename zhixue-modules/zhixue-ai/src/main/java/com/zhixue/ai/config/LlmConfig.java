package com.zhixue.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 大语言模型的配置类。
 * 这个类用来配置通义千问等 AI 模型的参数，比如 API 密钥和模型名称。
 * 从配置文件中读取这些参数，方便统一管理。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai.dashscope")
public class LlmConfig {

    /**
     * 调用 AI 模型需要的密钥。
     * 建议从环境变量 DASHSCOPE_API_KEY 读取，这样更安全。
     */
    private String apiKey;

    /**
     * 使用的模型名称。
     * 比如 qwen-plus 表示通义千问增强版模型。
     */
    private String model;
}


