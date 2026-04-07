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
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${interaction.danmaku-exchange:exchange_danmaku}")
    private String exchange;

    @Value("${interaction.danmaku-routing-key:danmaku.broadcast}")
    private String routingKey;
    @Value("${zhixue.integration.interaction-mq.mode:sandbox}")
    private String mqMode;

    public void send(DanmakuMessageDTO message) {
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
            rabbitTemplate.convertAndSend(exchange, routingKey, payload);
        } catch (JsonProcessingException e) {
            log.error("弹幕消息序列化失败", e);
            persistenceService.persist(message);
        } catch (Exception e) {
            log.error("发送弹幕 MQ 消息失败", e);
            persistenceService.persist(message);
        }
    }

    private boolean isStubMode() {
        return "stub".equalsIgnoreCase(mqMode);
    }
}
