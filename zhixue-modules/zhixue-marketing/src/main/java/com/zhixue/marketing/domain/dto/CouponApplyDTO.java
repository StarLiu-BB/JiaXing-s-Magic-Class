package com.zhixue.marketing.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单应用优惠券请求。
 */
@Data
public class CouponApplyDTO {

    /** 由服务端取自登录态并覆盖，客户端传入的值无效 */
    private Long userId;

    @NotNull(message = "用户优惠券记录ID不能为空")
    private Long couponUserId;

    @NotNull(message = "订单金额不能为空")
    private BigDecimal orderAmount;
}
