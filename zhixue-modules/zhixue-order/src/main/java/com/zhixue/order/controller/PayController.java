package com.zhixue.order.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.common.core.domain.R;
import com.zhixue.order.domain.dto.PayRequestDTO;
import com.zhixue.order.domain.dto.PayResponse;
import com.zhixue.order.domain.dto.PayResultMessage;
import com.zhixue.order.service.PayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付管理接口。
 * 这个类负责处理前端发来的关于支付的所有请求，包括发起支付、接收第三方支付平台的回调通知等操作。
 */
@Slf4j
@RestController
@RequestMapping("/pay")
@RequiredArgsConstructor
public class PayController {

    private final PayService payService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 支付结果交换机。
     */
    @Value("${order.pay-result-exchange:exchange_order}")
    private String payResultExchange;

    /**
     * 支付结果路由键。
     */
    @Value("${order.pay-result-routing-key:order.pay.result}")
    private String payResultRoutingKey;

    /**
     * 发起支付请求。
     * 调用第三方支付平台（支付宝或微信）生成支付链接或二维码。
     * @param dto 支付请求参数，包括订单号、支付渠道、用户编号等
     * @return 支付响应结果，包含支付链接或二维码
     */
    @PostMapping
    public R<PayResponse> pay(@Valid @RequestBody PayRequestDTO dto) {
        return R.ok(payService.pay(dto));
    }

    /**
     * 接收第三方支付平台的回调通知（模拟）。
     * 收到支付结果后，把结果发送到消息队列，由后台异步处理订单状态更新。
     * @param message 支付结果消息，包括订单号、支付状态、支付单号等
     * @return 处理结果
     */
    @PostMapping("/callback")
    public R<Void> callback(@RequestBody PayResultMessage message) {
        try {
            rabbitTemplate.convertAndSend(payResultExchange, payResultRoutingKey, objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException e) {
            log.error("支付回调消息序列化失败", e);
            return R.fail("回调处理失败");
        } catch (Exception e) {
            log.error("发送支付结果消息队列失败", e);
            return R.fail("回调处理失败");
        }
        return R.ok();
    }
}


