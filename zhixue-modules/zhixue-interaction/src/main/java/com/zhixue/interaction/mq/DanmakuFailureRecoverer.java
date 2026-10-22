package com.zhixue.interaction.mq;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class DanmakuFailureRecoverer implements MessageRecoverer {
    private final RabbitTemplate rabbit;
    private final String exchange;
    private final String routingKey;
    private final long timeoutMs;

    public DanmakuFailureRecoverer(RabbitTemplate rabbit, String exchange, String routingKey, long timeoutMs) {
        this.rabbit = rabbit; this.exchange = exchange; this.routingKey = routingKey; this.timeoutMs = timeoutMs;
    }

    @Override
    public void recover(Message message, Throwable cause) {
        try {
            rabbit.setMandatory(true);
            var correlation = new CorrelationData(UUID.randomUUID().toString());
            rabbit.send(exchange, routingKey, message, correlation);
            var confirm = correlation.getFuture().get(timeoutMs, TimeUnit.MILLISECONDS);
            if (!confirm.isAck() || correlation.getReturned() != null) throw new IllegalStateException("错误队列未确认");
        } catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new org.springframework.amqp.ImmediateRequeueAmqpException("错误队列不可用，保留原消息重试");
        }
    }
}
