package com.zhixue.order.service.impl;

import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.order.domain.dto.PayRequestDTO;
import com.zhixue.order.domain.dto.PayResponse;
import com.zhixue.order.domain.entity.Order;
import com.zhixue.order.service.OrderService;
import com.zhixue.order.service.PayService;
import com.zhixue.order.strategy.PayStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

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
     * @param userId 付款人，取自登录态
     * @param dto 支付请求参数
     * @return 支付响应结果，包含支付链接或二维码
     */
    @Override
    public PayResponse pay(Long userId, PayRequestDTO dto) {
        Order order = orderService.findByOrderNo(dto.getOrderNo());
        // 他人订单与不存在同样处理，避免通过订单号探测
        if (order == null || !order.getUserId().equals(userId)) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        if (order.getStatus() != 0) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "订单状态不可支付");
        }
        if (order.getExpireTime() == null || !order.getExpireTime().isAfter(LocalDateTime.now())) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "订单已过期，请重新下单");
        }
        if (orderService.hasPaid(userId, order.getProductId())) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "您已购买该课程，请勿重复支付");
        }
        String payChannel = dto.getPayChannel().toLowerCase(Locale.ROOT);
        PayStrategy strategy = chooseStrategy(payChannel);
        order.setPayChannel(payChannel);
        return strategy.pay(order);
    }

    /**
     * 根据支付渠道选择对应的支付策略。
     * @param channel 支付渠道，alipay 表示支付宝，wechat 表示微信支付
     * @return 支付策略对象
     */
    private PayStrategy chooseStrategy(String channel) {
        if (CollectionUtils.isEmpty(payStrategies)) {
            throw new ServiceException("支付策略未初始化");
        }
        if (!StringUtils.hasText(channel)) {
            throw new ServiceException("支付渠道不能为空");
        }
        return payStrategies.stream()
                .filter(s -> s.supports(channel))
                .findFirst()
                .orElseThrow(() -> new ServiceException("不支持的支付渠道: " + channel));
    }
}

