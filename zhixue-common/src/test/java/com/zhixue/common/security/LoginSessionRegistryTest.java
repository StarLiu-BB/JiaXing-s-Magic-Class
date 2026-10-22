package com.zhixue.common.security;

import com.zhixue.common.security.model.LoginUser;
import com.zhixue.common.security.session.LoginSessionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoginSessionRegistryTest {
    @SuppressWarnings("unchecked")
    @Test void oldLoginSnapshotCannotRegisterAfterAuthorizationChange() {
        RedisTemplate<String, Object> objects = mock(RedisTemplate.class);
        StringRedisTemplate strings = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(strings.opsForValue()).thenReturn(values);
        doReturn(new JdkSerializationRedisSerializer()).when(objects).getValueSerializer();
        when(objects.execute(any(RedisScript.class), any(org.springframework.data.redis.serializer.RedisSerializer.class),
                any(org.springframework.data.redis.serializer.RedisSerializer.class), anyList(), any(Object[].class))).thenReturn(0L);
        LoginSessionRegistry registry = new LoginSessionRegistry(objects, strings);
        LoginUser user = LoginUser.builder().userId(1003L).build();
        assertThatThrownBy(() -> registry.register("test", user, Duration.ofHours(2), "old"))
                .hasMessageContaining("授权已变更");
    }

    @SuppressWarnings("unchecked")
    @Test void legacySessionMustReloginRatherThanKeepRevokedPermissions() {
        RedisTemplate<String, Object> objects = mock(RedisTemplate.class);
        StringRedisTemplate strings = mock(StringRedisTemplate.class);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        when(objects.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(LoginUser.builder().userId(1003L).build());
        assertThat(new LoginSessionRegistry(objects, strings).resolve("legacy")).isNull();
        verifyNoInteractions(strings);
    }

    @SuppressWarnings("unchecked")
    @Test void metadataVersionMismatchRejectsCachedSession() {
        RedisTemplate<String, Object> objects = mock(RedisTemplate.class);
        StringRedisTemplate strings = mock(StringRedisTemplate.class);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        when(objects.opsForValue()).thenReturn(values);
        LoginUser user = LoginUser.builder().userId(1003L).sessionVersion("1").globalSessionVersion("0").build();
        when(values.get(anyString())).thenReturn(user);
        when(strings.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(0L);
        assertThat(new LoginSessionRegistry(objects, strings).resolve("revoked")).isNull();
    }
}
