package com.zhixue.marketing.service.impl;

import com.zhixue.common.core.domain.R;
import com.zhixue.marketing.service.AntiBrushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AntiBrushServiceImpl implements AntiBrushService {

    private final RedissonClient redissonClient;

    @Override
    public boolean check(Long userId, String ip, String deviceId) {
        try {
            boolean userAllowed = checkUserLevel(userId);
            boolean ipAllowed = checkIpLevel(ip);
            boolean deviceAllowed = checkDeviceLevel(deviceId);

            boolean allowed = userAllowed && ipAllowed && deviceAllowed;

            if (!allowed) {
                log.warn("防刷拦截，userId={}, ip={}, deviceId={}", userId, ip, deviceId);
            } else {
                log.info("防刷验证通过，userId={}, ip={}, deviceId={}", userId, ip, deviceId);
            }

            return allowed;
        } catch (Exception e) {
            log.error("防刷检查异常，userId={}, ip={}, deviceId={}, error={}", userId, ip, deviceId, e.getMessage());
            return false;
        }
    }

    private boolean checkUserLevel(Long userId) {
        if (userId == null) {
            log.warn("用户ID为空，userId={}", userId);
            return false;
        }

        RRateLimiter rateLimiter = redissonClient.getRateLimiter("coupon:claim:user:" + userId);
        return rateLimiter.tryAcquire(1);
    }

    private boolean checkIpLevel(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            log.warn("IP地址为空，ip={}", ip);
            return false;
        }

        RRateLimiter rateLimiter = redissonClient.getRateLimiter("coupon:claim:ip:" + ip);
        return rateLimiter.tryAcquire(10);
    }

    private boolean checkDeviceLevel(String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            log.warn("设备ID为空，deviceId={}", deviceId);
            return false;
        }

        RRateLimiter rateLimiter = redissonClient.getRateLimiter("coupon:claim:device:" + deviceId);
        return rateLimiter.tryAcquire(5);
    }

    @Override
    public boolean isBlacklisted(Long userId) {
        if (userId == null) {
            return false;
        }
        
        String key = "blacklist:user:" + userId;
        Boolean exists = redissonClient.getBucket(key).isExists();
        
        if (Boolean.TRUE.equals(exists)) {
            log.warn("用户在黑名单中，userId={}", userId);
        }
        
        return Boolean.TRUE.equals(exists);
    }
}
