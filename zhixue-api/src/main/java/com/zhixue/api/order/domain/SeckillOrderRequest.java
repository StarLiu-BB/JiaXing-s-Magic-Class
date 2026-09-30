package com.zhixue.api.order.domain;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 秒杀成交后请求订单服务生成订单。仅限服务间调用：价格由营销服务决定。
 */
@Data
public class SeckillOrderRequest {

    @NotNull(message = "用户编号不能为空")
    private Long userId;

    @NotNull(message = "商品编号不能为空")
    private Long productId;

    @NotNull(message = "秒杀价不能为空")
    @Positive(message = "秒杀价必须大于0")
    private BigDecimal amount;

    @NotNull(message = "活动编号不能为空")
    private Long activityId;
}
