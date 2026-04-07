package com.zhixue.order.strategy;

import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.order.domain.dto.PayResponse;
import com.zhixue.order.domain.entity.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 支付宝支付策略实现类。
 * 负责处理支付宝支付相关的逻辑，调用支付宝的支付接口生成支付链接。
 */
@Slf4j
@Component
public class AlipayStrategy implements PayStrategy {

    @Value("${zhixue.integration.order-pay.mode:${ZHIXUE_ORDER_PAY_MODE:sandbox}}")
    private String payMode;

    @Value("${order.pay.sandbox-base-url:${ZHIXUE_ORDER_PAY_SANDBOX_BASE_URL:https://sandbox-pay.zhixue.local}}")
    private String sandboxBaseUrl;

    @Value("${order.pay.alipay-gateway-url:${ZHIXUE_ALIPAY_GATEWAY_URL:}}")
    private String realGatewayUrl;

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
        String mode = "real".equalsIgnoreCase(payMode) ? "real" : "sandbox";
        String url = buildPayUrl(mode, order);
        log.info("支付宝支付下单完成 orderNo={}, mode={}, url={}", order.getOrderNo(), mode, url);
        return new PayResponse(url, "alipay", mode, order.getOrderNo());
    }

    private String buildPayUrl(String mode, Order order) {
        String orderNo = encode(order.getOrderNo());
        String amount = order.getAmount().setScale(2, RoundingMode.HALF_UP).toPlainString();
        if ("real".equals(mode)) {
            if (!StringUtils.hasText(realGatewayUrl)) {
                throw new ServiceException("支付宝 real 模式未配置网关地址");
            }
            return String.format("%s?channel=alipay&outTradeNo=%s&totalAmount=%s",
                    realGatewayUrl, orderNo, encode(amount));
        }
        String base = sandboxBaseUrl.endsWith("/") ? sandboxBaseUrl.substring(0, sandboxBaseUrl.length() - 1) : sandboxBaseUrl;
        return String.format("%s/alipay/checkout?orderNo=%s&amount=%s", base, orderNo, encode(amount));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

