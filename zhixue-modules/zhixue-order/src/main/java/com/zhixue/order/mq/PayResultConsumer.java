package com.zhixue.order.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.order.domain.dto.PayResultMessage;
import com.zhixue.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 支付结果消费者。
 * 监听支付结果消息队列，收到支付结果通知后，更新订单状态。
 * 支付成功则更新为已支付，支付失败则不做处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RabbitListener(queuesToDeclare = @Queue("${order.pay-result-queue:queue_order_pay_result}"))
public class PayResultConsumer {

    private final OrderService orderService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 处理支付结果消息。
     * 收到消息后，解析支付结果，如果是支付成功则更新订单状态。
     * @param message 支付结果消息的 JSON 字符串
     */
    @RabbitHandler
    public void onMessage(String message) {
        try {
            PayResultMessage payResult = objectMapper.readValue(message, PayResultMessage.class);
            if (payResult.getPayStatus() == 1) {
                orderService.paySuccess(payResult.getOrderNo(), payResult.getPayChannel(), payResult.getPayNo());
            } else {
                log.warn("支付失败，暂不处理 orderNo={}", payResult.getOrderNo());
            }
        } catch (Exception e) {
            log.error("处理支付结果消息失败 message={}", message, e);
        }
    }
}
