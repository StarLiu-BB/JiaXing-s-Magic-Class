package com.zhixue.interaction.netty;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * WebSocket 连接初始化器。
 * 作用：给每个新建立的 WebSocket 连接设置处理流水线，就像工厂里的生产线一样，
 * 让消息按照一定顺序经过编码、心跳检测、业务处理等步骤。
 */
@Component
@RequiredArgsConstructor
public class WsChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final HeartBeatHandler heartBeatHandler;
    private final DanmakuHandler danmakuHandler;

    /**
     * 初始化连接的处理流水线
     * 作用：给每个新连接设置消息处理的步骤，从编码解码到业务处理都在这里配置
     */
    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();
        // HTTP 编解码器，处理 WebSocket 握手阶段的 HTTP 请求
        p.addLast(new HttpServerCodec());
        // HTTP 消息聚合器，把 HTTP 消息合并成完整的消息
        p.addLast(new HttpObjectAggregator(64 * 1024));
        // 大文件传输处理器，支持分块传输
        p.addLast(new ChunkedWriteHandler());
        // 心跳处理器，处理空闲连接检测
        p.addLast(heartBeatHandler);
        // WebSocket 协议处理器，处理握手和消息帧
        p.addLast(new WebSocketServerProtocolHandler("/ws", null, true, 65536));
        // 弹幕处理器，处理实际的弹幕业务逻辑
        p.addLast(danmakuHandler);
    }
}


