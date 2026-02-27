package com.zhixue.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 脚本攻击防护过滤器
 * 作用：这个过滤器用来防止恶意脚本攻击。
 * 有些坏人会在请求参数里插入恶意的脚本代码，试图窃取用户信息。
 * 这个过滤器会检查请求参数，如果发现包含可疑的脚本关键字，就拒绝这个请求。
 */
@Component
public class XssFilter implements GlobalFilter, Ordered {

    private static final String[] DANGEROUS_PATTERNS = {
            "<script", "</script", "javascript:", "onerror", "onload", "alert("
    };

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String query = exchange.getRequest().getURI().getRawQuery();
        if (query != null) {
            String lower = query.toLowerCase();
            for (String pattern : DANGEROUS_PATTERNS) {
                if (lower.contains(pattern)) {
                    return reject(exchange, "请求参数存在非法脚本");
                }
            }
        }
        return chain.filter(exchange);
    }

    private Mono<Void> reject(@NonNull ServerWebExchange exchange, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", HttpStatus.BAD_REQUEST.value());
        body.put("msg", message);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            bytes = ("{\"code\":400,\"msg\":\"Bad Request\"}").getBytes(StandardCharsets.UTF_8);
        }
        exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -90;
    }
}

