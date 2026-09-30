package com.zhixue.order.strategy;

import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.order.domain.dto.PayResponse;
import com.zhixue.order.domain.entity.Order;
import org.springframework.stereotype.Component;

/**
 * 微信支付策略（尚未接入）。
 *
 * <p>小程序内只能使用微信支付，需要企业主体、商户号、APIv3 密钥与商户证书。
 * 接入 wechatpay-java 时：以 JSAPI 下单（需用户 openid），返回 wx.requestPayment 所需的
 * timeStamp / nonceStr / package / signType / paySign；回调改为 v3 验签 + 解密 resource。
 * 在此之前明确拒绝，而不是返回一个无法支付的伪造链接。</p>
 */
@Component
public class WechatPayStrategy implements PayStrategy {

    @Override
    public boolean supports(String payChannel) {
        return "wechat".equalsIgnoreCase(payChannel);
    }

    @Override
    public PayResponse pay(Order order) {
        throw new ServiceException(HttpStatus.BAD_REQUEST, "暂未接入微信支付，当前环境请使用模拟收银台");
    }
}
