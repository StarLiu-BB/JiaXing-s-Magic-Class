package com.zhixue.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 智谱 AI 配置属性类。
 * 从配置文件中读取智谱 AI 相关的配置参数。
 */
@Data
@ConfigurationProperties(prefix = "zhipuai")
public class ZhipuAiProperties {

    /**
     * 智谱 AI 的 API 密钥。
     * 建议从环境变量或 Nacos 配置中心读取，避免明文存储。
     */
    private String apiKey;

    /**
     * 默认使用的模型名称。
     * 可选值：glm-4、glm-4v、glm-3-turbo 等。
     */
    private String model = "glm-4-flash";

    /**
     * 请求超时时间（秒）。
     */
    private Integer timeout = 60;

    /**
     * 是否启用智谱 AI 服务。
     */
    private Boolean enabled = true;
}
