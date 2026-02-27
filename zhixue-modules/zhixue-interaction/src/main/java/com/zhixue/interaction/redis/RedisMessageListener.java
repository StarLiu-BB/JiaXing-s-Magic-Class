package com.zhixue.interaction.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.interaction.domain.dto.DanmakuMessageDTO;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis 订阅弹幕消息，转发给本机所有 WebSocket 连接。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisMessageListener implements MessageListener {

    private final RedisMessageListenerContainer container;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 所有连接的 Channel 集合。
     */
    @Getter
    private final ChannelGroup channelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    @PostConstruct
    public void init() {
        container.addMessageListener(this, new ChannelTopic(RedisMessagePublisher.CHANNEL));
        log.info("Redis 弹幕频道订阅完成，channel={}", RedisMessagePublisher.CHANNEL);
    }

    public void addChannel(Channel channel) {
        channelGroup.add(channel);
    }

    public void removeChannel(Channel channel) {
        channelGroup.remove(channel);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = redisTemplate.getStringSerializer().deserialize(message.getBody());
        if (body == null) {
            return;
        }
        try {
            DanmakuMessageDTO dto = objectMapper.readValue(body, DanmakuMessageDTO.class);
            channelGroup.writeAndFlush(new io.netty.handler.codec.http.websocketx.TextWebSocketFrame(body));
        } catch (Exception e) {
            log.error("Redis 弹幕消息处理失败, body={}", body, e);
        }
    }
}


