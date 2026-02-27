package com.zhixue.order.strategy;

import com.zhixue.order.domain.dto.PayResponse;
import com.zhixue.order.domain.entity.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 微信支付策略实现类。
 * 负责处理微信支付相关的逻辑，调用微信的支付接口生成支付链接或二维码。
 */
@Slf4j
@Component
public class WechatPayStrategy implements PayStrategy {

    /**
     * 判断是否支持微信支付。
     * @param payChannel 支付渠道
     * @return 如果是微信支付渠道返回 true，否则返回 false
     */
    @Override
    public boolean supports(String payChannel) {
        return "wechat".equalsIgnoreCase(payChannel);
    }

    /**
     * 发起微信支付。
     * 调用微信的支付接口生成支付链接或二维码，用户扫码后会跳转到微信收银台。
     * @param order 订单信息
     * @return 支付响应结果，包含微信支付链接或二维码
     */
    @Override
    public PayResponse pay(Order order) {
        // TODO: 调用微信支付生成预支付链接/二维码
        String url = "https://pay.wechat.com/mock/" + order.getOrderNo();
        log.info("微信支付下单成功 orderNo={}, url={}", order.getOrderNo(), url);
        return new PayResponse(url);
    }
}


