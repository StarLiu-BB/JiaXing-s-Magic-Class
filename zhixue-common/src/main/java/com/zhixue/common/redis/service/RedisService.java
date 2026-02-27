package com.zhixue.common.redis.service;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * Redis 常用操作封装，便于业务模块直接注入使用。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    public void set(@NotNull String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void set(@NotNull String key, Object value, long timeout, @NotNull TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    public void set(@NotNull String key, Object value, @NotNull Duration duration) {
        redisTemplate.opsForValue().set(key, value, duration);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(@NotNull String key) {
        return (T) redisTemplate.opsForValue().get(key);
    }

    public boolean delete(@NotNull String key) {
        Boolean result = redisTemplate.delete(key);
        return Boolean.TRUE.equals(result);
    }

    public long delete(@NotNull Collection<String> keys) {
        Long result = redisTemplate.delete(keys);
        return result == null ? 0 : result;
    }

    public boolean expire(@NotNull String key, long timeout, @NotNull TimeUnit unit) {
        Boolean result = redisTemplate.expire(key, timeout, unit);
        return Boolean.TRUE.equals(result);
    }

    public boolean expire(@NotNull String key, @NotNull Duration duration) {
        Boolean result = redisTemplate.expire(key, duration);
        return Boolean.TRUE.equals(result);
    }

    public boolean hasKey(@NotNull String key) {
        Boolean result = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(result);
    }

    public long increment(@NotNull String key, long delta) {
        Long result = redisTemplate.opsForValue().increment(key, delta);
        return result == null ? 0 : result;
    }

    public long decrement(@NotNull String key, long delta) {
        return increment(key, -delta);
    }
}

