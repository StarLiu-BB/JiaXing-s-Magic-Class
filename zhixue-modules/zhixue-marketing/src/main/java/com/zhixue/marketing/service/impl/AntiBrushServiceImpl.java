package com.zhixue.marketing.service.impl;

import com.zhixue.marketing.service.AntiBrushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 多维度防刷服务：按用户、IP、设备分别做令牌桶限流。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AntiBrushServiceImpl implements AntiBrushService {

    private final RedissonClient redissonClient;

    /** 单用户每分钟可领券次数。 */
    @Value("${marketing.anti-brush.user-rate-per-minute:5}")
    private long userRatePerMinute;

    /** 单 IP 每分钟可领券次数。 */
    @Value("${marketing.anti-brush.ip-rate-per-minute:30}")
    private long ipRatePerMinute;

    /** 单设备每分钟可领券次数。 */
    @Value("${marketing.anti-brush.device-rate-per-minute:20}")
    private long deviceRatePerMinute;

    /** 限流器空闲后的过期时间，避免 key 无限堆积。 */
    @Value("${marketing.anti-brush.limiter-ttl-hours:2}")
    private long limiterTtlHours;

    @Override
    public boolean check(Long userId, String ip, String deviceId) {
        // 标识缺失属于调用方参数问题，直接拒绝，无需访问 Redis
        if (userId == null) {
            log.warn("防刷拦截：用户ID为空");
            return false;
        }
        if (isBlank(ip)) {
            log.warn("防刷拦截：IP 为空, userId={}", userId);
            return false;
        }
        if (isBlank(deviceId)) {
            log.warn("防刷拦截：设备ID为空, userId={}", userId);
            return false;
        }

        try {
            boolean allowed = tryAcquire("coupon:claim:user:" + userId, userRatePerMinute)
                    && tryAcquire("coupon:claim:ip:" + ip, ipRatePerMinute)
                    && tryAcquire("coupon:claim:device:" + deviceId, deviceRatePerMinute);

            if (!allowed) {
                log.warn("防刷拦截，userId={}, ip={}, deviceId={}", userId, ip, deviceId);
            }
            return allowed;
        } catch (Exception e) {
            // 限流组件不可用时放行并告警：防刷是增强手段，
            // 库存与限领等基础校验仍在下游生效；
            // 若此处静默拒绝，Redis 抖动会让整个营销活动瘫痪。
            log.error("防刷检查异常，降级放行，userId={}, ip={}, deviceId={}", userId, ip, deviceId, e);
            return true;
        }
    }

    /**
     * 获取限流器并确保速率已初始化。
     *
     * <p>trySetRate 是幂等的：已配置过则返回 false 且不覆盖，
     * 因此每次调用都执行是安全的。若省略该步骤，
     * 未初始化的限流器调用 tryAcquire 会直接抛异常。</p>
     */
    private boolean tryAcquire(String key, long permitsPerMinute) {
        RRateLimiter limiter = redissonClient.getRateLimiter(key);
        limiter.trySetRate(RateType.OVERALL, permitsPerMinute, 1, RateIntervalUnit.MINUTES);
        limiter.expire(java.time.Duration.ofHours(limiterTtlHours));
        return limiter.tryAcquire(1);
    }

    @Override
    public boolean isBlacklisted(Long userId) {
        if (userId == null) {
            return false;
        }
        try {
            boolean exists = redissonClient.getBucket("blacklist:user:" + userId).isExists();
            if (exists) {
                log.warn("用户在黑名单中，userId={}", userId);
            }
            return exists;
        } catch (Exception e) {
            // 黑名单不可用时不应阻断正常用户
            log.error("黑名单查询异常，按非黑名单处理，userId={}", userId, e);
            return false;
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
