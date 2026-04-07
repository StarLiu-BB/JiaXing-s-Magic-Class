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
    private final ObjectMapper objectMapper = new ObjectMapper();

    @RabbitListener(queuesToDeclare = @Queue("${interaction.danmaku-queue:queue_danmaku}"))
    public void onMessage(String message) {
        try {
            DanmakuMessageDTO dto = objectMapper.readValue(message, DanmakuMessageDTO.class);
            persistenceService.persist(dto);
        } catch (Exception e) {
            log.error("消费弹幕消息失败, body={}", message, e);
        }
    }
}
