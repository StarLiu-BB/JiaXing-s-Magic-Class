package com.zhixue.order.strategy;

import com.zhixue.order.domain.dto.PayResponse;
import com.zhixue.order.domain.entity.Order;

/**
 * 支付策略接口。
 * 定义了支付策略的通用方法，不同的支付平台（支付宝、微信）需要实现这个接口。
 * 使用策略模式，方便扩展新的支付渠道。
 */
public interface PayStrategy {

    /**
     * 判断是否支持当前支付渠道。
     * @param payChannel 支付渠道，alipay 表示支付宝，wechat 表示微信支付
     * @return 如果支持返回 true，否则返回 false
     */
    boolean supports(String payChannel);

    /**
     * 发起支付。
     * 调用第三方支付平台的接口，生成支付链接或二维码。
     * @param order 订单信息
     * @return 支付响应结果，包含支付链接或二维码
     */
    PayResponse pay(Order order);
}


