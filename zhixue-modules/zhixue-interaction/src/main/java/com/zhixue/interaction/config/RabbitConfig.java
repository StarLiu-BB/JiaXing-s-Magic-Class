package com.zhixue.interaction.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置类。
 * 配置弹幕相关的队列、交换机和绑定关系。
 */
@Configuration
@ConditionalOnExpression("'${zhixue.integration.interaction-mq.mode:sandbox}' != 'stub'")
public class RabbitConfig {

    @Value("${interaction.danmaku-queue:queue_danmaku}")
    private String danmakuQueue;

    @Value("${interaction.danmaku-exchange:exchange_danmaku}")
    private String danmakuExchange;

    @Value("${interaction.danmaku-routing-key:danmaku.broadcast}")
    private String danmakuRoutingKey;

    @Bean
    public DirectExchange danmakuErrorExchange() { return new DirectExchange(danmakuExchange + ".error", true, false); }

    @Bean
    public Queue danmakuErrorQueue() { return new Queue(danmakuQueue + ".error", true); }

    @Bean
    public Binding danmakuErrorBinding() {
        return BindingBuilder.bind(danmakuErrorQueue()).to(danmakuErrorExchange()).with("failed");
    }

    @Bean
    public org.springframework.amqp.rabbit.retry.MessageRecoverer danmakuRecoverer(org.springframework.amqp.rabbit.core.RabbitTemplate rabbit) {
        return new com.zhixue.interaction.mq.DanmakuFailureRecoverer(rabbit, danmakuExchange + ".error", "failed", 3000);
    }

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
