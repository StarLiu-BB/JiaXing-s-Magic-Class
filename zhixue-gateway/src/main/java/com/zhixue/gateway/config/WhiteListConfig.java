package com.zhixue.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * 白名单配置类
 * 作用：这个类用来配置不需要登录就能访问的接口路径。
 * 比如登录接口、注册接口，用户还没登录就能访问，这些接口就放在白名单里。
 * 白名单里的路径不需要验证登录凭证，直接放行。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "security")
public class WhiteListConfig {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private List<String> whitelist = new ArrayList<>();

    public boolean matches(String path) {
        return whitelist.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }
}

