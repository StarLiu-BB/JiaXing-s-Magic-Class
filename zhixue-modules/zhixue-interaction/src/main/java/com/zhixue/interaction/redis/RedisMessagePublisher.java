package com.zhixue.interaction.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.interaction.domain.dto.DanmakuMessageDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 发布弹幕消息，用于多节点广播。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisMessagePublisher {

    public static final String CHANNEL = "interaction:danmaku";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void publish(DanmakuMessageDTO message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(CHANNEL, payload);
        } catch (JsonProcessingException e) {
            log.error("Redis 发布弹幕消息序列化失败", e);
        }
    }
}


