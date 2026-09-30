package com.zhixue.order.strategy;

import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.order.domain.dto.PayResponse;
import com.zhixue.order.domain.entity.Order;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 支付渠道不得伪装成功。
 *
 * <p>修复前：支付宝/微信策略 sandbox 模式返回指向不存在域名 sandbox-pay.zhixue.local 的链接，
 * real 模式也只是拼接一个未签名的 URL，没有任何真实渠道交互，却对调用方返回"下单成功"。</p>
 */
class PayChannelHonestyTest {

    private Order order() {
        Order order = new Order();
        order.setOrderNo("ORD-1");
        order.setAmount(new BigDecimal("199.00"));
        return order;
    }

    @Test
    void alipayMustFailLoudlyUntilRealSdkIsIntegrated() {
        AlipayStrategy strategy = new AlipayStrategy();
        assertThat(strategy.supports("alipay")).isTrue();
        assertThatThrownBy(() -> strategy.pay(order()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("暂未接入");
    }

    @Test
    void wechatMustFailLoudlyUntilRealSdkIsIntegrated() {
        WechatPayStrategy strategy = new WechatPayStrategy();
        assertThat(strategy.supports("wechat")).isTrue();
        assertThatThrownBy(() -> strategy.pay(order()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("暂未接入");
    }

    @Test
    void mockCashierMustBeDisabledByDefault() {
        // 模拟收银台等于"免费付款按钮"，默认关闭，只有显式开启的联调环境才可用
        MockPayStrategy strategy = new MockPayStrategy();
        assertThat(strategy.supports("mock")).isTrue();
        assertThatThrownBy(() -> strategy.pay(order()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未启用");
    }

    @Test
    void mockCashierWhenEnabledReturnsAmountForCheckoutDisplay() {
        MockPayStrategy strategy = new MockPayStrategy();
        ReflectionTestUtils.setField(strategy, "enabled", true);

        PayResponse response = strategy.pay(order());

        assertThat(response.getPayChannel()).isEqualTo("mock");
        assertThat(response.getMode()).isEqualTo("mock");
        assertThat(response.getOrderNo()).isEqualTo("ORD-1");
        assertThat(response.getAmount()).isEqualByComparingTo("199.00");
        // 模拟收银台不返回任何外部跳转地址，避免被误认为真实渠道
        assertThat(response.getPayUrl()).isNull();
    }
}
