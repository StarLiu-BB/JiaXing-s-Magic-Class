package com.zhixue.order.strategy;

import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.order.domain.dto.PayResponse;
import com.zhixue.order.domain.entity.Order;
import org.springframework.stereotype.Component;

/**
 * 支付宝支付策略（尚未接入）。
 *
 * <p>此前 sandbox 模式返回指向不存在域名的链接、real 模式拼接未签名的 URL，
 * 没有任何真实渠道交互却对调用方返回"下单成功"。在拿到商户资质并接入
 * alipay-sdk（RSA2 签名下单 + 平台公钥验签回调）之前，明确拒绝而非伪装成功。</p>
 *
 * <p>接入时：在此实现下单，并把 PayCallbackVerifier 替换为支付宝验签；
 * 网关白名单需放行 /pay/callback 供支付宝服务器回调。</p>
 */
@Component
public class AlipayStrategy implements PayStrategy {

    @Override
    public boolean supports(String payChannel) {
        return "alipay".equalsIgnoreCase(payChannel);
    }

    @Override
    public PayResponse pay(Order order) {
        throw new ServiceException(HttpStatus.BAD_REQUEST, "暂未接入支付宝，当前环境请使用模拟收银台");
    }
}
