package com.zhixue.interaction.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.interaction.domain.dto.DanmakuMessageDTO;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.GlobalEventExecutor;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis 订阅弹幕消息，转发给本机对应房间的 WebSocket 连接。
 *
 * <p>连接按 roomId 分组保存：广播时只投递给同房间的连接，
 * 避免 A 课程的弹幕出现在 B 课程。</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisMessageListener implements MessageListener {

    private final RedisMessageListenerContainer container;
    private final StringRedisTemplate redisTemplate;
    /** 必须注入 Spring 托管的实例：它已注册 JavaTimeModule，否则含 LocalDateTime 的弹幕无法反序列化 */
    private final ObjectMapper objectMapper;

    /** roomId -> 该房间的连接集合。 */
    private final Map<Long, ChannelGroup> roomChannels = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        container.addMessageListener(this, new ChannelTopic(RedisMessagePublisher.CHANNEL));
        log.info("Redis 弹幕频道订阅完成，channel={}", RedisMessagePublisher.CHANNEL);
    }

    /**
     * 把连接加入指定房间。同一连接只应属于一个房间。
     */
    public void addChannel(Channel channel, Long roomId) {
        if (channel == null || roomId == null) {
            return;
        }
        roomChannels.computeIfAbsent(roomId,
                k -> new DefaultChannelGroup(GlobalEventExecutor.INSTANCE)).add(channel);
    }

    /**
     * 连接断开时从所属房间移除，并清理空房间避免内存泄漏。
     */
    public void removeChannel(Channel channel) {
        if (channel == null) {
            return;
        }
        roomChannels.forEach((roomId, group) -> group.remove(channel));
        roomChannels.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = redisTemplate.getStringSerializer().deserialize(message.getBody());
        if (body == null) {
            return;
        }
        try {
            DanmakuMessageDTO dto = objectMapper.readValue(body, DanmakuMessageDTO.class);
            if (dto.getRoomId() == null) {
                // 无法定向投递的消息直接丢弃，绝不退化为全量广播
                log.warn("弹幕消息缺少 roomId，已丢弃");
                return;
            }
            ChannelGroup group = roomChannels.get(dto.getRoomId());
            if (group == null || group.isEmpty()) {
                return;
            }
            group.writeAndFlush(new TextWebSocketFrame(body));
        } catch (Exception e) {
            log.error("Redis 弹幕消息处理失败, body={}", body, e);
        }
    }
}
