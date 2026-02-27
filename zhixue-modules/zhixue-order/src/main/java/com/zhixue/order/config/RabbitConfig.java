package com.zhixue.order.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 配置类。
 * 配置订单相关的队列、交换机和绑定关系。
 */
@Configuration
public class RabbitConfig {

    @Value("${order.pay-result-queue:queue_order_pay_result}")
    private String payResultQueue;

    @Value("${order.pay-result-exchange:exchange_order}")
    private String payResultExchange;

    @Value("${order.pay-result-routing-key:order.pay.result}")
    private String payResultRoutingKey;

    @Value("${order.timeout-queue:queue_order_timeout}")
    private String timeoutQueue;

    @Value("${order.timeout-exchange:exchange_order_delay}")
    private String timeoutExchange;

    @Value("${order.timeout-routing-key:order.timeout}")
    private String timeoutRoutingKey;

    // ==================== 支付结果配置 ====================

    @Bean
    public Queue payResultQueue() {
        return new Queue(payResultQueue, true);
    }

    @Bean
    public DirectExchange payResultExchange() {
        return new DirectExchange(payResultExchange);
    }

    @Bean
    public Binding payResultBinding() {
        return BindingBuilder.bind(payResultQueue())
                .to(payResultExchange())
                .with(payResultRoutingKey);
    }

    // ==================== 订单超时配置（延时队列） ====================

    @Bean
    public Queue timeoutQueue() {
        return new Queue(timeoutQueue, true);
    }

    @Bean
    public CustomExchange timeoutExchange() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-delayed-type", "direct");
        return new CustomExchange(timeoutExchange, "x-delayed-message", true, false, args);
    }

    @Bean
    public Binding timeoutBinding() {
        return BindingBuilder.bind(timeoutQueue())
                .to(timeoutExchange())
                .with(timeoutRoutingKey)
                .noargs();
    }
}
