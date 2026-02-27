package com.zhixue.order.service.impl;

import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.order.domain.dto.PayRequestDTO;
import com.zhixue.order.domain.dto.PayResponse;
import com.zhixue.order.domain.entity.Order;
import com.zhixue.order.service.OrderService;
import com.zhixue.order.service.PayService;
import com.zhixue.order.strategy.PayStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * 支付服务实现类。
 * 负责处理支付相关的业务逻辑，主要是发起支付请求。
 * 根据用户选择的支付渠道（支付宝或微信），调用对应的支付策略生成支付链接或二维码。
 */
@Service
@RequiredArgsConstructor
public class PayServiceImpl implements PayService {

    private final OrderService orderService;
    private final List<PayStrategy> payStrategies;

    /**
     * 发起支付请求。
     * 先检查订单是否存在、用户是否有权支付、订单状态是否可以支付。
     * 然后根据支付渠道选择对应的支付策略，生成支付链接或二维码。
     * @param dto 支付请求参数
     * @return 支付响应结果，包含支付链接或二维码
     */
    @Override
    public PayResponse pay(PayRequestDTO dto) {
        Order order = orderService.findByOrderNo(dto.getOrderNo());
        if (order == null) {
            throw new ServiceException("订单不存在");
        }
        if (!order.getUserId().equals(dto.getUserId())) {
            throw new ServiceException("无权支付该订单");
        }
        if (order.getStatus() != 0) {
            throw new ServiceException("订单状态不可支付");
        }
        PayStrategy strategy = chooseStrategy(dto.getPayChannel());
        if (strategy == null) {
            throw new ServiceException("不支持的支付渠道");
        }
        order.setPayChannel(dto.getPayChannel());
        return strategy.pay(order);
    }

    /**
     * 根据支付渠道选择对应的支付策略。
     * @param channel 支付渠道，alipay 表示支付宝，wechat 表示微信支付
     * @return 支付策略对象
     */
    private PayStrategy chooseStrategy(String channel) {
        if (CollectionUtils.isEmpty(payStrategies)) {
            return null;
        }
        return payStrategies.stream()
                .filter(s -> s.supports(channel))
                .findFirst()
                .orElse(null);
    }
}


