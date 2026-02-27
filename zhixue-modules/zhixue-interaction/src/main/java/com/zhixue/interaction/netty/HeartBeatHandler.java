package com.zhixue.interaction.netty;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 心跳与空闲连接检测处理类。
 * 作用：维持 WebSocket 连接的活力，定期检查连接是否还能用。
 * 如果用户很久没发消息（超过 60 秒），就自动断开连接，避免占用资源。
 * 同时处理客户端的心跳请求，确保连接不被网络设备断开。
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class HeartBeatHandler extends SimpleChannelInboundHandler<Object> {

    /**
     * 构造方法
     * 作用：创建心跳处理器实例
     */
    public HeartBeatHandler() {
        super(false);
    }

    /**
     * 当有新的连接建立时调用
     * 作用：给连接加上心跳检测功能，设置 60 秒没消息就断开
     */
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        // 给连接添加一个心跳检测处理器，60秒没收到消息就触发断开
        ctx.pipeline().addBefore(ctx.name(), "idleStateHandler",
                new IdleStateHandler(60, 0, 0, TimeUnit.SECONDS));
    }

    /**
     * 处理接收到的各种消息
     * 作用：处理心跳消息（Ping/Pong），并把其他消息传递给下一个处理器
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof PingWebSocketFrame) {
            // 收到客户端的 Ping 消息，回复 Pong 消息保持连接
            ctx.channel().writeAndFlush(new PongWebSocketFrame());
            return;
        }
        if (msg instanceof FullHttpRequest) {
            // 把 HTTP 握手请求传递给下一个处理器处理
            ctx.fireChannelRead(msg);
            return;
        }
        // 其他类型的消息也传递给下一个处理器处理
        ctx.fireChannelRead(msg);
    }

    /**
     * 处理用户事件，主要是心跳超时事件
     * 作用：当用户长时间（60秒）没有发送消息时，自动断开连接，释放资源
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        // 检查是否是心跳超时事件
        if (evt instanceof IdleStateEvent idle) {
            // 如果是读取空闲（用户很久没发消息）
            if (idle.state() == IdleState.READER_IDLE) {
                log.info("channel {} 心跳超时，关闭连接", ctx.channel().id());
                // 断开连接
                ctx.close();
            }
        }
    }
}