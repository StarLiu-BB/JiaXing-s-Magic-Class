package com.zhixue.common.security.session;

import com.zhixue.common.core.constant.CacheConstants;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.security.model.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LoginSessionRegistry {
    private static final String REVISION = "zhixue:auth:revision";
    private static final String GLOBAL = "zhixue:auth:global-version";
    private static final String VERSION = "zhixue:auth:user-version:";
    private static final String TOKENS = "zhixue:auth:user-tokens:";
    private static final DefaultRedisScript<Long> REGISTER = new DefaultRedisScript<>("""
            if (redis.call('GET', KEYS[1]) or '0') ~= ARGV[1] then return 0 end
            local version = redis.call('GET', KEYS[2]) or '0'
            local global = redis.call('GET', KEYS[3]) or '0'
            if version ~= ARGV[2] or global ~= ARGV[3] then return 0 end
            local now = redis.call('TIME')
            local ms = now[1] * 1000 + math.floor(now[2] / 1000)
            redis.call('ZREMRANGEBYSCORE', KEYS[4], '-inf', ms)
            redis.call('SET', KEYS[5], ARGV[4], 'PX', ARGV[5])
            redis.call('ZADD', KEYS[4], ms + ARGV[5], KEYS[5])
            local ttl = redis.call('PTTL', KEYS[4])
            if ttl < tonumber(ARGV[5]) then redis.call('PEXPIRE', KEYS[4], ARGV[5]) end
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> VALIDATE = new DefaultRedisScript<>("""
            if (redis.call('GET', KEYS[1]) or '0') ~= ARGV[1] then return 0 end
            if (redis.call('GET', KEYS[2]) or '0') ~= ARGV[2] then return 0 end
            return redis.call('EXISTS', KEYS[3])
            """, Long.class);
    private static final DefaultRedisScript<Long> REVOKE = new DefaultRedisScript<>("""
            redis.call('INCR', KEYS[1])
            redis.call('INCR', KEYS[2])
            local tokens = redis.call('ZRANGE', KEYS[3], 0, -1)
            for _, key in ipairs(tokens) do redis.call('DEL', key) end
            redis.call('DEL', KEYS[3])
            return #tokens
            """, Long.class);
    private static final DefaultRedisScript<Long> REVOKE_ALL = new DefaultRedisScript<>("""
            redis.call('INCR', KEYS[1])
            redis.call('INCR', KEYS[2])
            return 1
            """, Long.class);

    private final RedisTemplate<String, Object> objects;
    private final StringRedisTemplate strings;

    public String revision() { return value(REVISION); }
    private String value(String key) {
        String value = strings.opsForValue().get(key);
        return value == null ? "0" : value;
    }

    @SuppressWarnings("unchecked")
    public void register(String token, LoginUser user, Duration ttl, String expectedRevision) {
        user.setSessionVersion(value(VERSION + user.getUserId()));
        user.setGlobalSessionVersion(value(GLOBAL));
        user.setAuthorizationRevision(expectedRevision);
        RedisSerializer<Object> serializer = (RedisSerializer<Object>) objects.getValueSerializer();
        byte[] payload = serializer.serialize(user);
        if (payload == null) throw new ServiceException("会话序列化失败");
        byte[][] args = {
                expectedRevision.getBytes(StandardCharsets.UTF_8),
                user.getSessionVersion().getBytes(StandardCharsets.UTF_8),
                user.getGlobalSessionVersion().getBytes(StandardCharsets.UTF_8),
                payload, String.valueOf(ttl.toMillis()).getBytes(StandardCharsets.UTF_8)
        };
        Long accepted = objects.execute(REGISTER, RedisSerializer.byteArray(), new org.springframework.data.redis.serializer.GenericToStringSerializer<>(Long.class),
                List.of(REVISION, VERSION + user.getUserId(), GLOBAL, TOKENS + user.getUserId(), CacheConstants.LOGIN_TOKEN_KEY + token), (Object[]) args);
        if (!Long.valueOf(1).equals(accepted)) throw new ServiceException(409, "授权已变更，请重新登录");
    }

    public LoginUser resolve(String token) {
        if (token == null || token.isBlank()) return null;
        Object cached = objects.opsForValue().get(CacheConstants.LOGIN_TOKEN_KEY + token);
        if (!(cached instanceof LoginUser user) || user.getUserId() == null
                || user.getSessionVersion() == null || user.getGlobalSessionVersion() == null) return null;
        Long accepted = strings.execute(VALIDATE,
                List.of(VERSION + user.getUserId(), GLOBAL, CacheConstants.LOGIN_TOKEN_KEY + token),
                user.getSessionVersion(), user.getGlobalSessionVersion());
        return Long.valueOf(1).equals(accepted) ? user : null;
    }

    public long revokeUser(Long userId) {
        Long result = strings.execute(REVOKE, List.of(REVISION, VERSION + userId, TOKENS + userId));
        if (result == null) throw new ServiceException("撤销会话失败");
        return result;
    }

    public void revokeAll() {
        if (strings.execute(REVOKE_ALL, List.of(REVISION, GLOBAL)) == null) throw new ServiceException("撤销会话失败");
    }

    public void logout(String token) {
        if (token == null || token.isBlank()) return;
        Object cached = objects.opsForValue().get(CacheConstants.LOGIN_TOKEN_KEY + token);
        objects.delete(CacheConstants.LOGIN_TOKEN_KEY + token);
        if (cached instanceof LoginUser user && user.getUserId() != null) {
            strings.opsForZSet().remove(TOKENS + user.getUserId(), CacheConstants.LOGIN_TOKEN_KEY + token);
        }
    }
}
