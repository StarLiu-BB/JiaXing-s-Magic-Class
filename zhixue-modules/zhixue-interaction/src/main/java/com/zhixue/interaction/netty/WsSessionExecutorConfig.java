package com.zhixue.interaction.netty;

import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.RejectedExecutionHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WsSessionExecutorConfig {
    @Bean(destroyMethod = "shutdownGracefully")
    public DefaultEventExecutorGroup sessionExecutor() {
        return new DefaultEventExecutorGroup(4, null, 1024, RejectedExecutionHandlers.reject());
    }
}
