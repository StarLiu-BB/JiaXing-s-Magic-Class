package com.zhixue.common.swagger.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <p>
 * Knife4j & SpringDoc 统一配置，提供基础文档信息以及认证定义。
 * </p>
 */
@Configuration
@RequiredArgsConstructor
public class Knife4jConfig {

    private static final String TOKEN_HEADER = "Authorization";

    @Bean
    @ConditionalOnMissingBean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("智学云在线教育平台")
                        .version("1.0.0")
                        .description("Zhixue Cloud API 文档"))
                .components(new Components()
                        .addSecuritySchemes("BearerToken", new SecurityScheme()
                                .name(TOKEN_HEADER)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("BearerToken"));
    }
}

