package com.zhixue.gateway.config;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 限流配置类
 * 作用：这个类用来配置接口的访问限流规则。
 * 当某个接口访问人数过多时，系统会自动拦截多余的请求，防止服务器崩溃。
 * 被拦截的请求会收到一个友好的提示信息："当前访问人数过多，请稍后重试"。
 */
@Configuration
public class SentinelGatewayConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public ErrorWebExceptionHandler customSentinelBlockExceptionHandler() {
        return (ServerWebExchange exchange, Throwable ex) -> {
            if (isBlockException(ex)) {
                exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                String body = "{\"code\":429,\"msg\":\"当前访问人数过多，请稍后重试\"}";
                return exchange.getResponse().writeWith(Mono.just(
                        exchange.getResponse().bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))
                ));
            }
            return Mono.error(ex);
        };
    }

    private boolean isBlockException(Throwable ex) {
        Throwable cause = ex;
        while (cause != null) {
            if (cause instanceof BlockException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}

