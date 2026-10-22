package com.zhixue.interaction.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.interaction.domain.dto.DanmakuMessageDTO;
import com.zhixue.interaction.service.DanmakuPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * 弹幕 MQ 消费者，异步写库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${zhixue.integration.interaction-mq.mode:sandbox}' != 'stub'")
public class DanmakuConsumer {

    private final DanmakuPersistenceService persistenceService;
    /** 必须注入 Spring 托管的实例：它已注册 JavaTimeModule，否则含 LocalDateTime 的弹幕无法反序列化 */
    private final ObjectMapper objectMapper;

    @RabbitListener(queuesToDeclare = @Queue("${interaction.danmaku-queue:queue_danmaku}"))
    public void onMessage(String message) {
        DanmakuMessageDTO dto;
        try {
            dto = objectMapper.readValue(message, DanmakuMessageDTO.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
            throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("弹幕消息格式不合法");
        }
        if (dto == null) throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("弹幕消息为空");
        if (dto.getMessageId() == null) {
            dto.setMessageId("legacy-" + org.springframework.util.DigestUtils.md5DigestAsHex(message.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        persistenceService.persist(dto);
    }
}
