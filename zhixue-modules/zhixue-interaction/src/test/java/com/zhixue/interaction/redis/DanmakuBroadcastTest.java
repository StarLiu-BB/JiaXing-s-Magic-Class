package com.zhixue.interaction.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.interaction.domain.dto.DanmakuMessageDTO;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 弹幕广播链路测试。
 *
 * <p>覆盖阶段 2 验收标准：</p>
 * <ul>
 *   <li>D3：含 LocalDateTime 的弹幕 DTO 必须能被序列化，否则广播全链路失效</li>
 *   <li>D4：广播必须按 roomId 隔离，A 房间的弹幕不得出现在 B 房间</li>
 * </ul>
 */
class DanmakuBroadcastTest {

    /** 与 Spring Boot 默认一致的 ObjectMapper（已注册 JavaTimeModule）。 */
    private final ObjectMapper springObjectMapper = Jackson2ObjectMapperBuilder.json().build();

    private DanmakuMessageDTO danmaku(Long roomId, String content) {
        DanmakuMessageDTO dto = new DanmakuMessageDTO();
        dto.setRoomId(roomId);
        dto.setUserId(9001L);
        dto.setContent(content);
        dto.setTimePoint(5);
        dto.setSendTime(LocalDateTime.now());
        return dto;
    }

    @Test
    void shouldSerializeDanmakuContainingLocalDateTime() {
        // D3 根因：裸 new ObjectMapper() 没有 JavaTimeModule，
        // 遇到 LocalDateTime 会抛 InvalidDefinitionException，导致广播静默失败
        DanmakuMessageDTO dto = danmaku(1001L, "hello");

        assertThatCode(() -> springObjectMapper.writeValueAsString(dto))
                .doesNotThrowAnyException();
    }

    @Test
    void publisherShouldActuallyPublishInsteadOfSwallowingError() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisMessagePublisher publisher = new RedisMessagePublisher(redisTemplate, springObjectMapper);

        publisher.publish(danmaku(1001L, "hello"));

        // 序列化成功才会真正发到 Redis；若失败会被 catch 吞掉，convertAndSend 不会被调用
        org.mockito.Mockito.verify(redisTemplate)
                .convertAndSend(org.mockito.ArgumentMatchers.eq(RedisMessagePublisher.CHANNEL),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldOnlyDeliverToChannelsInSameRoom() throws Exception {
        RedisMessageListener listener = newListener();

        EmbeddedChannel roomA = new EmbeddedChannel();
        EmbeddedChannel roomB = new EmbeddedChannel();
        listener.addChannel(roomA, 1001L);
        listener.addChannel(roomB, 1002L);

        String payload = springObjectMapper.writeValueAsString(danmaku(1001L, "only-for-1001"));
        listener.onMessage(new DefaultMessage(
                RedisMessagePublisher.CHANNEL.getBytes(StandardCharsets.UTF_8),
                payload.getBytes(StandardCharsets.UTF_8)), null);

        TextWebSocketFrame received = roomA.readOutbound();
        assertThat(received).as("同房间必须收到弹幕").isNotNull();
        assertThat(received.text()).contains("only-for-1001");

        assertThat((Object) roomB.readOutbound())
                .as("异房间不得收到弹幕（跨房间串播）")
                .isNull();
    }

    @Test
    void shouldStopDeliveringAfterChannelRemoved() throws Exception {
        RedisMessageListener listener = newListener();

        EmbeddedChannel channel = new EmbeddedChannel();
        listener.addChannel(channel, 1001L);
        listener.removeChannel(channel);

        String payload = springObjectMapper.writeValueAsString(danmaku(1001L, "after-leave"));
        listener.onMessage(new DefaultMessage(
                RedisMessagePublisher.CHANNEL.getBytes(StandardCharsets.UTF_8),
                payload.getBytes(StandardCharsets.UTF_8)), null);

        assertThat((Object) channel.readOutbound())
                .as("已离开房间的连接不应再收到弹幕")
                .isNull();
    }

    @Test
    void shouldIgnoreMessageWithoutRoomId() throws Exception {
        RedisMessageListener listener = newListener();
        EmbeddedChannel channel = new EmbeddedChannel();
        listener.addChannel(channel, 1001L);

        listener.onMessage(new DefaultMessage(
                RedisMessagePublisher.CHANNEL.getBytes(StandardCharsets.UTF_8),
                "{\"userId\":1,\"content\":\"no room\"}".getBytes(StandardCharsets.UTF_8)), null);

        assertThat((Object) channel.readOutbound())
                .as("缺少 roomId 的消息无法定向投递，应丢弃而非广播给所有人")
                .isNull();
    }

    private RedisMessageListener newListener() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.getStringSerializer())
                .thenReturn(org.springframework.data.redis.serializer.RedisSerializer.string());
        return new RedisMessageListener(
                mock(org.springframework.data.redis.listener.RedisMessageListenerContainer.class),
                redisTemplate,
                springObjectMapper);
    }
}
