package com.zhixue.order.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.common.core.domain.R;
import com.zhixue.order.domain.dto.PayRequestDTO;
import com.zhixue.order.domain.dto.PayResponse;
import com.zhixue.order.domain.dto.PayResultMessage;
import com.zhixue.order.domain.entity.Order;
import com.zhixue.order.security.PayCallbackVerifier;
import com.zhixue.order.service.OrderService;
import com.zhixue.order.service.PayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付管理接口。
 */
@Slf4j
@RestController
@RequestMapping("/pay")
@RequiredArgsConstructor
public class PayController {

    private final PayService payService;
    private final OrderService orderService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final PayCallbackVerifier payCallbackVerifier;

    @Value("${order.pay-result-exchange:exchange_order}")
    private String payResultExchange;

    @Value("${order.pay-result-routing-key:order.pay.result}")
    private String payResultRoutingKey;

    @Value("${zhixue.integration.order-mq.mode:sandbox}")
    private String mqMode;

    @PostMapping
    public R<PayResponse> pay(@Valid @RequestBody PayRequestDTO dto) {
        return R.ok(payService.pay(dto));
    }

    @PostMapping("/callback")
    public R<Void> callback(@RequestBody PayResultMessage message) {
        if (!StringUtils.hasText(message.getOrderNo())) {
            return R.fail("订单号不能为空");
        }
        if (message.getPayStatus() == null) {
            return R.fail("支付状态不能为空");
        }
        if (message.getPayStatus() != 1) {
            log.info("收到非成功支付回调，忽略 orderNo={}, payStatus={}", message.getOrderNo(), message.getPayStatus());
            return R.ok();
        }

        // 回调接口公网可达且无登录态，必须先验签并核对金额，再决定是否推进订单状态。
        // 校验失败一律拒绝，不得降级放行。
        Order order = orderService.findByOrderNo(message.getOrderNo());
        if (order == null) {
            log.warn("支付回调对应订单不存在 orderNo={}", message.getOrderNo());
            return R.fail("订单不存在");
        }
        payCallbackVerifier.verify(message, order.getAmount());

        if (!"real".equalsIgnoreCase(mqMode)) {
            orderService.paySuccess(message.getOrderNo(), message.getPayChannel(), message.getPayNo());
            return R.ok();
        }

        try {
            rabbitTemplate.convertAndSend(payResultExchange, payResultRoutingKey, objectMapper.writeValueAsString(message));
            return R.ok();
        } catch (JsonProcessingException e) {
            log.error("支付回调消息序列化失败，降级同步处理 orderNo={}", message.getOrderNo(), e);
            orderService.paySuccess(message.getOrderNo(), message.getPayChannel(), message.getPayNo());
            return R.ok();
        } catch (Exception e) {
            log.error("发送支付结果消息失败，降级同步处理 orderNo={}", message.getOrderNo(), e);
            orderService.paySuccess(message.getOrderNo(), message.getPayChannel(), message.getPayNo());
            return R.ok();
        }
    }
}
