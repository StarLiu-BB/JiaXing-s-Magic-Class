package com.zhixue.order.strategy;

import com.zhixue.order.domain.dto.PayResponse;
import com.zhixue.order.domain.entity.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 支付宝支付策略实现类。
 * 负责处理支付宝支付相关的逻辑，调用支付宝的支付接口生成支付链接。
 */
@Slf4j
@Component
public class AlipayStrategy implements PayStrategy {

    /**
     * 判断是否支持支付宝支付。
     * @param payChannel 支付渠道
     * @return 如果是支付宝渠道返回 true，否则返回 false
     */
    @Override
    public boolean supports(String payChannel) {
        return "alipay".equalsIgnoreCase(payChannel);
    }

    /**
     * 发起支付宝支付。
     * 调用支付宝的支付接口生成支付链接，用户点击链接后会跳转到支付宝收银台。
     * @param order 订单信息
     * @return 支付响应结果，包含支付宝支付链接
     */
    @Override
    public PayResponse pay(Order order) {
        // TODO: 调用支付宝支付生成链接
        String url = "https://pay.alipay.com/mock/" + order.getOrderNo();
        log.info("支付宝支付下单成功 orderNo={}, url={}", order.getOrderNo(), url);
        return new PayResponse(url);
    }
}


