package com.zhixue.interaction.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.interaction.domain.dto.DanmakuMessageDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 弹幕 MQ 生产者，用于异步写库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DanmakuProducer {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${interaction.danmaku-exchange:exchange_danmaku}")
    private String exchange;

    @Value("${interaction.danmaku-routing-key:danmaku.broadcast}")
    private String routingKey;

    public void send(DanmakuMessageDTO message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            rabbitTemplate.convertAndSend(exchange, routingKey, payload);
        } catch (JsonProcessingException e) {
            log.error("弹幕消息序列化失败", e);
        } catch (Exception e) {
            log.error("发送弹幕 MQ 消息失败", e);
        }
    }
}
