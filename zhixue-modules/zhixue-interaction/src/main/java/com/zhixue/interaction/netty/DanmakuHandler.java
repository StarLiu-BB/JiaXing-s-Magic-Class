package com.zhixue.interaction.netty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.interaction.domain.dto.DanmakuMessageDTO;
import com.zhixue.interaction.mq.DanmakuProducer;
import com.zhixue.interaction.redis.RedisMessageListener;
import com.zhixue.interaction.redis.RedisMessagePublisher;
import com.zhixue.interaction.sensitive.SensitiveWordFilter;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 弹幕处理核心类。
 * 作用：处理用户发送的弹幕消息，包括敏感词过滤、广播给其他用户，以及把弹幕存到数据库里。
 * 它是 WebSocket 连接的核心处理器，负责管理连接和消息流转。
 */
@Slf4j
@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class DanmakuHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private final RedisMessageListener redisMessageListener;
    private final RedisMessagePublisher redisMessagePublisher;
    private final DanmakuProducer danmakuProducer;
    private final SensitiveWordFilter sensitiveWordFilter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 当有新的 WebSocket 连接建立时调用
     * 作用：把新连接保存起来，这样后面可以给这个连接发消息
     */
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        // 把新建立的连接加入到连接列表中，方便后续广播消息
        redisMessageListener.addChannel(channel);
        log.info("WebSocket 连接建立：{}", channel.id());
    }

    /**
     * 当 WebSocket 连接关闭时调用
     * 作用：把关闭的连接从列表中移除，避免后续给无效连接发消息
     */
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        // 连接关闭时，从连接列表中移除，清理资源
        redisMessageListener.removeChannel(channel);
        log.info("WebSocket 连接关闭：{}", channel.id());
    }

    /**
     * 处理用户发送的弹幕消息
     * 作用：接收用户发送的弹幕，进行敏感词过滤，然后广播给其他用户，并异步保存到数据库
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) throws Exception {
        String text = frame.text();
        try {
            // 把用户发送的 JSON 消息转换成 Java 对象
            DanmakuMessageDTO dto = objectMapper.readValue(text, DanmakuMessageDTO.class);
            dto.setSendTime(LocalDateTime.now());
            
            // 检查并过滤弹幕里的敏感词，把敏感词替换成 *
            dto.setContent(sensitiveWordFilter.filter(dto.getContent()));

            // 通过 Redis 把弹幕广播给所有连接的用户，确保大家都能看到同一条弹幕
            redisMessagePublisher.publish(dto);

            // 把弹幕消息放到消息队列里，异步保存到数据库，避免影响实时性能
            danmakuProducer.send(dto);
        } catch (ServiceException e) {
            log.warn("弹幕业务异常: {}", e.getMessage());
            ctx.channel().writeAndFlush(new TextWebSocketFrame("{\"code\":400,\"msg\":\"" + e.getMessage() + "\"}"));
        } catch (Exception e) {
            log.error("处理弹幕消息失败, body={}", text, e);
            ctx.channel().writeAndFlush(new TextWebSocketFrame("{\"code\":500,\"msg\":\"处理失败\"}"));
        }
    }
}