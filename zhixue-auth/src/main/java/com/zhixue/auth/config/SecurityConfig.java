package com.zhixue.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 安全配置类
 * 作用：这个类用来配置认证服务的安全策略。
 * 因为认证服务本身提供登录接口，所以这里把默认的安全检查都关掉了。
 * 包括关闭防跨站攻击、表单登录、基础认证等功能，让所有请求都能通过。
 * 这样用户才能访问登录接口进行登录操作。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF（前后端分离不需要）
            .csrf(AbstractHttpConfigurer::disable)
            // 禁用 form login
            .formLogin(AbstractHttpConfigurer::disable)
            // 禁用 http basic
            .httpBasic(AbstractHttpConfigurer::disable)
            // 禁用 session（使用 JWT）
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 放行所有请求（auth 服务的接口都是公开的）
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            );
        return http.build();
    }
}
