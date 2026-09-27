package com.zhixue.auth.service;

import com.zhixue.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录失败计数与锁定测试。
 *
 * <p>覆盖 D12：登录接口原先没有任何失败次数限制、账号锁定或限流，
 * 叠加 Sentinel 未真正生效（D21），可无限次暴力破解密码。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginAttemptLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private LoginAttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        limiter = new LoginAttemptLimiter(redisTemplate);
        ReflectionTestUtils.setField(limiter, "maxAttempts", 5);
        ReflectionTestUtils.setField(limiter, "lockMinutes", 15);
    }

    @Test
    void shouldAllowWhenNoPreviousFailure() {
        when(valueOperations.get(anyString())).thenReturn(null);

        assertThatCode(() -> limiter.assertNotLocked("admin")).doesNotThrowAnyException();
    }

    @Test
    void shouldAllowWhenBelowThreshold() {
        when(valueOperations.get(anyString())).thenReturn("4");

        assertThatCode(() -> limiter.assertNotLocked("admin")).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectWhenThresholdReached() {
        when(valueOperations.get(anyString())).thenReturn("5");

        assertThatThrownBy(() -> limiter.assertNotLocked("admin"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("锁定");
    }

    @Test
    void shouldSetExpiryOnFirstFailureSoLockAutoReleases() {
        when(valueOperations.increment(anyString())).thenReturn(1L);

        limiter.recordFailure("admin");

        // 首次失败必须设置过期时间，否则计数永不清零、账号被永久锁死
        verify(redisTemplate).expire(anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void shouldNotResetExpiryOnSubsequentFailures() {
        when(valueOperations.increment(anyString())).thenReturn(3L);

        limiter.recordFailure("admin");

        // 后续失败不应刷新 TTL，否则攻击者可通过持续尝试无限延长锁定窗口
        verify(redisTemplate, never()).expire(anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void shouldClearCounterAfterSuccessfulLogin() {
        limiter.recordSuccess("admin");

        verify(redisTemplate).delete(anyString());
    }

    @Test
    void shouldIgnoreBlankIdentifier() {
        assertThatCode(() -> limiter.assertNotLocked("  ")).doesNotThrowAnyException();
        limiter.recordFailure("  ");
        verify(valueOperations, never()).increment(anyString());
    }

    @Test
    void shouldFailOpenWhenRedisUnavailable() {
        // Redis 故障不应让所有人无法登录
        when(valueOperations.get(anyString()))
                .thenThrow(new RuntimeException("Redis down"));

        assertThatCode(() -> limiter.assertNotLocked("admin"))
                .as("计数器不可用时应放行并告警，而非阻断全部登录")
                .doesNotThrowAnyException();
    }
}
