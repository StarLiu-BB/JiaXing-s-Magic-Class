package com.zhixue.interaction.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.interaction.domain.dto.DanmakuMessageDTO;
import com.zhixue.interaction.service.DanmakuPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 弹幕 MQ 生产者，用于异步写库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DanmakuProducer {

    private final ObjectProvider<RabbitTemplate> rabbitTemplateProvider;
    private final DanmakuPersistenceService persistenceService;
    /** 必须注入 Spring 托管的实例：它已注册 JavaTimeModule，否则含 LocalDateTime 的弹幕无法序列化 */
    private final ObjectMapper objectMapper;

    @Value("${interaction.danmaku-exchange:exchange_danmaku}")
    private String exchange;

    @Value("${interaction.danmaku-routing-key:danmaku.broadcast}")
    private String routingKey;
    @Value("${zhixue.integration.interaction-mq.mode:sandbox}")
    private String mqMode;

    @Value("${interaction.danmaku.confirm-timeout-ms:3000}")
    private long confirmTimeoutMs = 3000;

    public void send(DanmakuMessageDTO message) {
        if (message.getMessageId() == null) message.setMessageId(java.util.UUID.randomUUID().toString());
        if (isStubMode()) {
            persistenceService.persist(message);
            return;
        }
        RabbitTemplate rabbitTemplate = rabbitTemplateProvider.getIfAvailable();
        if (rabbitTemplate == null) {
            log.warn("RabbitTemplate 不可用，降级为直写数据库");
            persistenceService.persist(message);
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(message);
            rabbitTemplate.setMandatory(true);
            var correlation = new org.springframework.amqp.rabbit.connection.CorrelationData(message.getMessageId());
            rabbitTemplate.convertAndSend(exchange, routingKey, payload, correlation);
            var confirm = correlation.getFuture().get(confirmTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!confirm.isAck() || correlation.getReturned() != null) throw new IllegalStateException("MQ未确认可靠路由");
        } catch (Exception unavailable) {
            if (unavailable instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("弹幕MQ投递未确认，回落幂等数据库写入");
            persistenceService.persist(message);
        }
    }

    private boolean isStubMode() {
        return "stub".equalsIgnoreCase(mqMode);
    }
}
