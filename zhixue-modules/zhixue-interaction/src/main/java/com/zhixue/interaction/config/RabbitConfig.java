package com.zhixue.interaction.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置类。
 * 配置弹幕相关的队列、交换机和绑定关系。
 */
@Configuration
public class RabbitConfig {

    @Value("${interaction.danmaku-queue:queue_danmaku}")
    private String danmakuQueue;

    @Value("${interaction.danmaku-exchange:exchange_danmaku}")
    private String danmakuExchange;

    @Value("${interaction.danmaku-routing-key:danmaku.broadcast}")
    private String danmakuRoutingKey;

    /**
     * 弹幕队列。
     */
    @Bean
    public Queue danmakuQueue() {
        return new Queue(danmakuQueue, true);
    }

    /**
     * 弹幕交换机。
     */
    @Bean
    public DirectExchange danmakuExchange() {
        return new DirectExchange(danmakuExchange);
    }

    /**
     * 绑定弹幕队列到交换机。
     */
    @Bean
    public Binding danmakuBinding() {
        return BindingBuilder.bind(danmakuQueue())
                .to(danmakuExchange())
                .with(danmakuRoutingKey);
    }
}
