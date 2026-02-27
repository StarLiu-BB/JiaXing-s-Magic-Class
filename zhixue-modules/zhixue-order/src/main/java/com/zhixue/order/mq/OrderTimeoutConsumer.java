package com.zhixue.order.mq;

import com.zhixue.order.domain.entity.Order;
import com.zhixue.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 订单超时取消消费者。
 * 监听订单超时消息队列，收到超时消息后，检查订单是否已支付。
 * 如果订单还是待支付状态且已超过过期时间，则自动取消订单。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RabbitListener(queuesToDeclare = @Queue("${order.timeout-queue:queue_order_timeout}"))
public class OrderTimeoutConsumer {

    private final OrderService orderService;

    /**
     * 处理订单超时消息。
     * 收到消息后，查询订单信息，如果订单还是待支付状态且已超过过期时间，则自动取消订单。
     * @param orderNo 订单编号
     */
    @RabbitHandler
    public void onMessage(String orderNo) {
        try {
            Order order = orderService.findByOrderNo(orderNo);
            if (order == null) {
                return;
            }
            if (order.getStatus() != 0) {
                return;
            }
            if (order.getExpireTime() != null && order.getExpireTime().isAfter(LocalDateTime.now())) {
                // 未过期，可能延时级别过粗，忽略
                return;
            }
            orderService.cancelOrder(orderNo, "超时未支付，系统自动取消");
            log.info("订单超时已取消 orderNo={}", orderNo);
        } catch (Exception e) {
            log.error("处理订单超时消息失败 orderNo={}", orderNo, e);
        }
    }
}
