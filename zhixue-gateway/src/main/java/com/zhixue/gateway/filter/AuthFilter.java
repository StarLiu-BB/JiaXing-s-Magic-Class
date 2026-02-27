package com.zhixue.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.utils.JwtUtils;
import com.zhixue.common.core.utils.StringUtils;
import com.zhixue.common.security.config.SecurityProperties;
import com.zhixue.gateway.config.WhiteListConfig;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 登录验证过滤器
 * 作用：这个过滤器用来检查用户是否已经登录。
 * 每个请求到达网关时，都会先经过这个过滤器。
 * 如果请求的路径在白名单里（比如登录接口），就直接放行。
 * 否则，就从请求头里取出登录凭证，验证凭证是否有效。
 * 验证通过后，把用户信息添加到请求头里，转发给后面的服务。
 */
@Component
@RequiredArgsConstructor
public class AuthFilter implements GlobalFilter, Ordered {

    private final WhiteListConfig whiteListConfig;
    private final SecurityProperties securityProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (whiteListConfig.matches(path)) {
            return chain.filter(exchange);
        }

        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.isBlank(header) || !header.startsWith("Bearer ")) {
            return unauthorized(exchange, "未登录或令牌缺失");
        }

        String token = header.substring(7);
        Claims claims;
        try {
            claims = JwtUtils.parseClaims(token, securityProperties.getJwtSecret());
        } catch (Exception e) {
            return unauthorized(exchange, "令牌无效或已过期");
        }

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(httpHeaders -> {
                    httpHeaders.set("X-User-Id", String.valueOf(claims.getOrDefault("userId", "")));
                    httpHeaders.set("X-User-Name", String.valueOf(claims.getOrDefault("username", "")));
                    Object roles = claims.get("roles");
                    httpHeaders.set("X-User-Roles", roles == null ? "" : roles.toString());
                })
                .build();
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private Mono<Void> unauthorized(@NotNull ServerWebExchange exchange, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", HttpStatus.UNAUTHORIZED);
        body.put("msg", message);
        return writeResponse(exchange, body, HttpStatus.UNAUTHORIZED);
    }

    private Mono<Void> writeResponse(@NotNull ServerWebExchange exchange, Map<String, Object> body, int status) {
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            bytes = ("{\"code\":" + status + ",\"msg\":\"系统错误\"}").getBytes(StandardCharsets.UTF_8);
        }
        exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.valueOf(status));
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        @NotNull DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}

