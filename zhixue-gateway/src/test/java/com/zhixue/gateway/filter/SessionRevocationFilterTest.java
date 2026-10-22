package com.zhixue.gateway.filter;

import com.zhixue.common.core.utils.JwtUtils;
import com.zhixue.common.security.config.SecurityProperties;
import com.zhixue.common.security.model.LoginUser;
import com.zhixue.common.security.session.LoginSessionRegistry;
import com.zhixue.gateway.config.WhiteListConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SessionRevocationFilterTest {
    @Test void validJwtWithRevokedSessionGets401AndNeverReachesService() {
        String secret = "test-only-secret-test-only-secret-123456789";
        SecurityProperties props = new SecurityProperties(); props.setJwtSecret(secret);
        LoginSessionRegistry sessions = mock(LoginSessionRegistry.class);
        AuthFilter filter = new AuthFilter(new WhiteListConfig(), props, sessions);
        String token = JwtUtils.generateToken("1003", Map.of(), secret, 600);
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/order/list").header("Authorization", "Bearer " + token));
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        filter.filter(exchange, chain).block();
        assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(chain);
        verify(sessions).resolve(token);
    }

    @Test void activeSessionPreservesTrustedUserContext() {
        String secret = "test-only-secret-test-only-secret-123456789";
        SecurityProperties props = new SecurityProperties(); props.setJwtSecret(secret); props.setInternalToken("test-only-internal");
        LoginSessionRegistry sessions = mock(LoginSessionRegistry.class);
        String token = JwtUtils.generateToken("1003", Map.of(), secret, 600);
        when(sessions.resolve(token)).thenReturn(LoginUser.builder().userId(1003L).username("student").roles(List.of("STUDENT")).build());
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/order/list").header("Authorization", "Bearer " + token));
        new AuthFilter(new WhiteListConfig(), props, sessions).filter(exchange, forwarded -> {
            assertThat(forwarded.getRequest().getHeaders().getFirst("X-User-Id")).isEqualTo("1003");
            return reactor.core.publisher.Mono.empty();
        }).block();
    }
}
