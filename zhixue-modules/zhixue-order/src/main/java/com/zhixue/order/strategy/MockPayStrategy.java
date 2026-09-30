package com.zhixue.order.strategy;

import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.order.domain.dto.PayResponse;
import com.zhixue.order.domain.entity.Order;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 模拟收银台（本地联调用，不产生真实扣款）。
 *
 * <p>在尚未取得支付商户资质时，用它代替真实渠道完成"下单 → 付款 → 回调入账"的完整闭环。
 * 用户在收银台确认后，由 PayController 以渠道身份签名并走与真实回调完全相同的
 * 验签 + 金额核对 + 幂等入账路径。</p>
 *
 * <p>它本质上是一个"免费付款按钮"，因此默认关闭（fail-closed），
 * 只有显式设置 ZHIXUE_PAY_MOCK_ENABLED=true 的环境才可用，开启时启动日志会告警。</p>
 */
@Slf4j
@Component
public class MockPayStrategy implements PayStrategy {

    public static final String CHANNEL = "mock";

    @Value("${zhixue.integration.order-pay.mock-enabled:false}")
    private boolean enabled;

    @PostConstruct
    void warnIfEnabled() {
        if (enabled) {
            log.warn("============================================================");
            log.warn(" 模拟收银台已启用：任何登录用户都可以不付款直接完成订单。");
            log.warn(" 仅限本地联调。生产环境必须保持 ZHIXUE_PAY_MOCK_ENABLED=false。");
            log.warn("============================================================");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean supports(String payChannel) {
        return CHANNEL.equalsIgnoreCase(payChannel);
    }

    @Override
    public PayResponse pay(Order order) {
        if (!enabled) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "模拟收银台未启用");
        }
        return new PayResponse(null, CHANNEL, CHANNEL, order.getOrderNo(), order.getAmount());
    }
}
