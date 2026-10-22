package com.zhixue.auth.service;

import com.zhixue.auth.service.impl.TokenServiceImpl;
import com.zhixue.common.security.config.SecurityProperties;
import com.zhixue.common.security.model.LoginUser;
import com.zhixue.common.security.session.LoginSessionRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TokenRevocationTest {
    private final LoginSessionRegistry registry = mock(LoginSessionRegistry.class);
    private TokenServiceImpl service() {
        SecurityProperties properties = new SecurityProperties();
        properties.setJwtSecret(Base64.getEncoder().encodeToString("unit-test-key-unit-test-key-0123456789".getBytes()));
        return new TokenServiceImpl(registry, properties);
    }

    @Test void eachLoginHasDistinctJtiAndUsesCapturedRevision() {
        LoginUser user = LoginUser.builder().userId(1003L).username("test").roles(List.of("STUDENT")).authorizationRevision("4").build();
        TokenServiceImpl service = service();
        String first = service.createToken(user);
        String second = service.createToken(user);
        assertThat(second).isNotEqualTo(first);
        verify(registry, times(2)).register(anyString(), eq(user), any(Duration.class), eq("4"));
    }

    @Test void refreshRevisionConflictCannotIssueNewTokenOrDeleteOldBeforeRegistration() {
        when(registry.revision()).thenReturn("5");
        LoginUser user = LoginUser.builder().userId(1003L).username("test").roles(List.of("STUDENT")).build();
        when(registry.resolve("old")).thenReturn(user);
        doThrow(new com.zhixue.common.core.exception.ServiceException(409, "授权已变更"))
                .when(registry).register(anyString(), eq(user), any(Duration.class), eq("5"));
        assertThatThrownBy(() -> service().refreshToken("old")).hasMessageContaining("授权已变更");
        verify(registry, never()).logout("old");
    }

    @Test void revokedTokenCannotRefresh() {
        when(registry.revision()).thenReturn("5");
        assertThat(service().refreshToken("revoked")).isNull();
        verify(registry, never()).register(anyString(), any(), any(), anyString());
    }
}
