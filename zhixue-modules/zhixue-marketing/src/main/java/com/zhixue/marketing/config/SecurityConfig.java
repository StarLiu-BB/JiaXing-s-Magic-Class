package com.zhixue.marketing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 营销模块安全配置类
 * 作用：配置营销模块的安全规则，比如哪些接口需要登录、是否允许跨站请求等。
 * 目前这个配置是允许所有请求通过，因为营销相关的接口大多是公开访问的。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 配置安全过滤链
     * 作用：设置具体的安全规则，包括是否启用防跨站攻击、会话管理方式等。
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 关闭防跨站请求伪造保护（因为接口都是通过令牌认证的）
            .csrf(AbstractHttpConfigurer::disable)
            // 使用无状态会话（不存储用户会话信息，完全依赖令牌认证）
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 配置请求授权规则：允许所有请求通过
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            );
        return http.build();
    }
}
