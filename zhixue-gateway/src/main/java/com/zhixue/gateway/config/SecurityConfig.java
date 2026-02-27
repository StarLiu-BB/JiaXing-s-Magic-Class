package com.zhixue.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * 网关安全配置类
 * 作用：这个类用来配置网关的安全策略。
 * 因为网关自己负责处理用户登录验证，所以这里把默认的安全检查都关掉了。
 * 包括关闭防跨站攻击、表单登录、基础认证等功能，让所有请求都能通过网关。
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            // 禁用 CSRF（前后端分离不需要）
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            // 禁用 form login
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            // 禁用 http basic
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            // 放行所有请求（网关自己处理认证）
            .authorizeExchange(exchanges -> exchanges
                .anyExchange().permitAll()
            );
        return http.build();
    }
}
