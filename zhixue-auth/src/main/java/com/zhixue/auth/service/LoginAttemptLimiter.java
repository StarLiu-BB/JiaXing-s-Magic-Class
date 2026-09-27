package com.zhixue.auth.service;

import com.zhixue.common.core.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * 登录失败计数与临时锁定。
 *
 * <p>登录接口在网关白名单内、无需任何凭据即可反复调用，
 * 若不限制失败次数即可无限暴力破解密码。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginAttemptLimiter {

    private static final String KEY_PREFIX = "zhixue:auth:login:fail:";

    private final StringRedisTemplate redisTemplate;

    /** 允许的连续失败次数，达到即锁定。 */
    @Value("${zhixue.auth.login.max-attempts:5}")
    private int maxAttempts;

    /** 锁定时长（分钟），到期自动解锁。 */
    @Value("${zhixue.auth.login.lock-minutes:15}")
    private int lockMinutes;

    /**
     * 校验该标识当前是否处于锁定状态。
     *
     * @param identifier 用户名或手机号
     */
    public void assertNotLocked(String identifier) {
        if (!StringUtils.hasText(identifier)) {
            return;
        }
        try {
            String value = redisTemplate.opsForValue().get(key(identifier));
            if (value == null) {
                return;
            }
            int attempts = Integer.parseInt(value);
            if (attempts >= maxAttempts) {
                log.warn("登录已被锁定，identifier={}, attempts={}", identifier, attempts);
                throw new ServiceException(
                        "登录失败次数过多，账号已被临时锁定，请 " + lockMinutes + " 分钟后再试");
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            // 计数器不可用时放行并告警：
            // 若此处抛错，Redis 抖动会导致所有人无法登录。
            log.error("登录失败计数读取异常，降级放行，identifier={}", identifier, e);
        }
    }

    /**
     * 记录一次登录失败。
     */
    public void recordFailure(String identifier) {
        if (!StringUtils.hasText(identifier)) {
            return;
        }
        try {
            Long attempts = redisTemplate.opsForValue().increment(key(identifier));
            // 仅首次失败时设置 TTL：后续刷新会让攻击者持续尝试即可无限延长锁定窗口
            if (attempts != null && attempts == 1L) {
                redisTemplate.expire(key(identifier), lockMinutes, TimeUnit.MINUTES);
            }
            log.warn("登录失败计数 identifier={}, attempts={}", identifier, attempts);
        } catch (Exception e) {
            log.error("登录失败计数写入异常，identifier={}", identifier, e);
        }
    }

    /**
     * 登录成功后清除计数。
     */
    public void recordSuccess(String identifier) {
        if (!StringUtils.hasText(identifier)) {
            return;
        }
        try {
            redisTemplate.delete(key(identifier));
        } catch (Exception e) {
            log.error("登录失败计数清理异常，identifier={}", identifier, e);
        }
    }

    private String key(String identifier) {
        return KEY_PREFIX + identifier;
    }
}
