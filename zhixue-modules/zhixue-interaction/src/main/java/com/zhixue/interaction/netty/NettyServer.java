package com.zhixue.interaction.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Netty WebSocket 服务器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NettyServer implements ApplicationRunner {

    @Value("${interaction.websocket-port:9999}")
    private int websocketPort;

    private final WsChannelInitializer wsChannelInitializer;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(wsChannelInitializer)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture f = b.bind(websocketPort).sync();
            serverChannel = f.channel();
            log.info("Netty WebSocket 服务器已启动，端口：{}", websocketPort);
        } catch (Exception e) {
            log.error("启动 Netty WebSocket 服务器失败", e);
            throw e;
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("关闭 Netty WebSocket 服务器");
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
    }
}


