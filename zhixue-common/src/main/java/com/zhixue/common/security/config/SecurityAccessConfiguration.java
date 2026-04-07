package com.zhixue.common.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 安全切面配置。
 */
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityAccessConfiguration {

    @Bean
    public SecurityAccessAspect securityAccessAspect() {
        return new SecurityAccessAspect();
    }

    @Bean
    public InternalAccessFilter internalAccessFilter(SecurityProperties securityProperties) {
        return new InternalAccessFilter(securityProperties);
    }
}
