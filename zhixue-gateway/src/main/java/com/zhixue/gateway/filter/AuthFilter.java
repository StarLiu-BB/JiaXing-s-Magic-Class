package com.zhixue.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.common.core.constant.CacheConstants;
import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.utils.JwtUtils;
import com.zhixue.common.core.utils.StringUtils;
import com.zhixue.common.redis.service.RedisService;
import com.zhixue.common.security.config.InternalAccessConstants;
import com.zhixue.common.security.config.SecurityProperties;
import com.zhixue.common.security.model.LoginUser;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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

    private static final List<String> INTERNAL_HEADERS = List.of(
            InternalAccessConstants.USER_ID_HEADER,
            InternalAccessConstants.USER_NAME_HEADER,
            InternalAccessConstants.USER_ROLES_HEADER,
            InternalAccessConstants.USER_PERMISSIONS_HEADER,
            InternalAccessConstants.INNER_CALL_HEADER,
            InternalAccessConstants.INTERNAL_TOKEN_HEADER
    );

    private final WhiteListConfig whiteListConfig;
    private final SecurityProperties securityProperties;
    private final RedisService redisService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        boolean whiteListed = whiteListConfig.matches(path);
        ServerHttpRequest sanitizedRequest = sanitizeHeaders(exchange.getRequest(), false);

        String token = extractBearerToken(sanitizedRequest.getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        if (StringUtils.isBlank(token)) {
            if (whiteListed) {
                return chain.filter(exchange.mutate().request(sanitizeHeaders(sanitizedRequest, true)).build());
            }
            return unauthorized(exchange, "未登录或令牌缺失");
        }

        LoginUser loginUser = resolveLoginUser(token);
        if (loginUser == null) {
            if (whiteListed) {
                return chain.filter(exchange.mutate().request(sanitizeHeaders(sanitizedRequest, true)).build());
            }
            return unauthorized(exchange, "令牌无效或已过期");
        }

        ServerHttpRequest mutatedRequest = sanitizeHeaders(sanitizedRequest, whiteListed).mutate()
                .headers(httpHeaders -> {
                    httpHeaders.set(InternalAccessConstants.USER_ID_HEADER, String.valueOf(loginUser.getUserId()));
                    httpHeaders.set(InternalAccessConstants.USER_NAME_HEADER, String.valueOf(loginUser.getUsername()));
                    httpHeaders.set(InternalAccessConstants.USER_ROLES_HEADER, String.join(",", safeList(loginUser.getRoles())));
                    httpHeaders.set(InternalAccessConstants.USER_PERMISSIONS_HEADER, String.join(",", extractPermissions(loginUser)));
                    httpHeaders.set(InternalAccessConstants.INTERNAL_TOKEN_HEADER, securityProperties.getInternalToken());
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

    private String extractBearerToken(String authorization) {
        if (StringUtils.isBlank(authorization) || !authorization.startsWith("Bearer ")) {
            return null;
        }
        return authorization.substring(7);
    }

    private LoginUser resolveLoginUser(String token) {
        try {
            Claims claims = JwtUtils.parseClaims(token, securityProperties.getJwtSecret());
            if (claims == null || !redisService.hasKey(CacheConstants.LOGIN_TOKEN_KEY + token)) {
                return null;
            }
            return redisService.get(CacheConstants.LOGIN_TOKEN_KEY + token);
        } catch (Exception e) {
            return null;
        }
    }

    private ServerHttpRequest sanitizeHeaders(ServerHttpRequest request, boolean stripAuthorization) {
        return request.mutate().headers(headers -> {
            INTERNAL_HEADERS.forEach(headers::remove);
            if (stripAuthorization) {
                headers.remove(HttpHeaders.AUTHORIZATION);
            }
        }).build();
    }

    @SuppressWarnings("unchecked")
    private List<String> extractPermissions(LoginUser loginUser) {
        if (loginUser.getExtra() == null) {
            return Collections.emptyList();
        }
        Object permissions = loginUser.getExtra().get("permissions");
        if (permissions instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return Collections.emptyList();
    }

    private List<String> safeList(List<String> values) {
        return values == null ? Collections.emptyList() : values;
    }
}
