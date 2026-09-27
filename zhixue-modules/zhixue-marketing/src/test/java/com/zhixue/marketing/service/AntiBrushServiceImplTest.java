package com.zhixue.marketing.service;

import com.zhixue.marketing.service.impl.AntiBrushServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 防刷服务测试。
 *
 * <p>覆盖 D25：原实现只调 {@code tryAcquire} 而从未调用 {@code trySetRate}。
 * 未初始化速率的 RRateLimiter 调用 tryAcquire 会抛异常，
 * 异常被 catch 后统一 {@code return false} → 所有请求恒被判为"刷单"，
 * 防刷检查实际是一个永久拒绝开关。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AntiBrushServiceImplTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RRateLimiter rateLimiter;

    @InjectMocks
    private AntiBrushServiceImpl antiBrushService;

    @Test
    void mustInitializeRateBeforeAcquiring() {
        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        when(rateLimiter.tryAcquire(anyLong())).thenReturn(true);

        antiBrushService.check(1001L, "127.0.0.1", "device-1");

        // 必须先设置速率，否则未初始化的限流器会抛异常
        verify(rateLimiter, atLeastOnce())
                .trySetRate(any(RateType.class), anyLong(), anyLong(), any(RateIntervalUnit.class));
    }

    @Test
    void shouldAllowWhenAllLimitsHaveQuota() {
        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        when(rateLimiter.tryAcquire(anyLong())).thenReturn(true);

        assertThat(antiBrushService.check(1001L, "127.0.0.1", "device-1"))
                .as("配额充足时必须放行，否则防刷会误杀全部正常请求")
                .isTrue();
    }

    @Test
    void shouldRejectWhenQuotaExhausted() {
        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        when(rateLimiter.tryAcquire(anyLong())).thenReturn(false);

        assertThat(antiBrushService.check(1001L, "127.0.0.1", "device-1")).isFalse();
    }

    @Test
    void shouldFailOpenWhenRedisUnavailable() {
        // Redis 故障不应让营销活动整体瘫痪：
        // 防刷是增强手段，基础校验（库存、限领）仍在下游生效
        when(redissonClient.getRateLimiter(anyString()))
                .thenThrow(new RuntimeException("Redis connection refused"));

        assertThat(antiBrushService.check(1001L, "127.0.0.1", "device-1"))
                .as("限流组件不可用时应放行并告警，而非静默拒绝所有请求")
                .isTrue();
    }

    @Test
    void shouldRejectBlankIdentifiersWithoutTouchingRedis() {
        assertThat(antiBrushService.check(null, "127.0.0.1", "device-1")).isFalse();
        verify(redissonClient, never()).getRateLimiter(anyString());
    }
}
