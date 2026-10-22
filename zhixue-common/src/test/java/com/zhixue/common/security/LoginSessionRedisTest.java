package com.zhixue.common.security;

import com.zhixue.common.redis.config.RedisConfig;
import com.zhixue.common.security.model.LoginUser;
import com.zhixue.common.security.session.LoginSessionRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@EnabledIfSystemProperty(named = "zhixue.test.isolated-redis", matches = "true")
class LoginSessionRedisTest {
    @Test void serializedSessionsAndRevocationAreAtomicInIsolatedRedis() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        var directory = Files.createTempDirectory(java.nio.file.Path.of("target"), "session-redis-");
        Process redis = new ProcessBuilder("redis-server", "--bind", "127.0.0.1", "--port", String.valueOf(port),
                "--save", "", "--appendonly", "no", "--dir", directory.toString())
                .redirectErrorStream(true).redirectOutput(directory.resolve("redis.log").toFile()).start();
        LettuceConnectionFactory connection = new LettuceConnectionFactory("127.0.0.1", port);
        try {
            connection.afterPropertiesSet(); connection.start();
            var strings = new StringRedisTemplate(connection);
            boolean ready = false;
            for (int i = 0; i < 50; i++) {
                try (var ping = strings.getConnectionFactory().getConnection()) { ping.ping(); ready = true; break; }
                catch (Exception e) { Thread.sleep(100); }
            }
            assertThat(ready).isTrue();
            var objects = new RedisConfig().redisTemplate(connection);
            var sessions = new LoginSessionRegistry(objects, strings);
            String revision = sessions.revision();
            LoginUser user = LoginUser.builder().userId(1003L).username("test").roles(List.of("STUDENT")).build();
            sessions.register("first", user, Duration.ofSeconds(60), revision);
            assertThat(sessions.resolve("first").getUserId()).isEqualTo(1003L);
            sessions.register("short", user, Duration.ofSeconds(5), revision);
            assertThat(strings.getExpire("zhixue:auth:user-tokens:1003")).isGreaterThan(50);
            assertThat(sessions.revokeUser(1003L)).isEqualTo(2);
            assertThat(sessions.resolve("first")).isNull();
            assertThat(sessions.resolve("short")).isNull();
            assertThatThrownBy(() -> sessions.register("stale", user, Duration.ofSeconds(60), revision)).hasMessageContaining("授权已变更");
            var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
            try {
                for (int i = 0; i < 10; i++) {
                    String captured = sessions.revision();
                    String key = "race-" + i;
                    var start = new java.util.concurrent.CountDownLatch(1);
                    var register = pool.submit(() -> {
                        start.await();
                        try { sessions.register(key, LoginUser.builder().userId(1003L).build(), Duration.ofSeconds(60), captured); }
                        catch (com.zhixue.common.core.exception.ServiceException conflict) { assertThat(conflict.getCode()).isEqualTo(409); }
                        return null;
                    });
                    var revoke = pool.submit(() -> { start.await(); sessions.revokeUser(1003L); return null; });
                    start.countDown(); register.get(5, java.util.concurrent.TimeUnit.SECONDS); revoke.get(5, java.util.concurrent.TimeUnit.SECONDS);
                    assertThat(sessions.resolve(key)).isNull();
                }
            } finally { pool.shutdownNow(); }
            sessions.register("new", user, Duration.ofSeconds(60), sessions.revision());
            sessions.revokeAll();
            assertThat(sessions.resolve("new")).isNull();
            sessions.register("logout", user, Duration.ofSeconds(60), sessions.revision());
            sessions.logout("logout");
            assertThat(sessions.resolve("logout")).isNull();
        } finally {
            connection.destroy(); redis.destroy();
            if (!redis.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) redis.destroyForcibly();
            Files.deleteIfExists(directory.resolve("redis.log")); Files.deleteIfExists(directory);
        }
    }
}
