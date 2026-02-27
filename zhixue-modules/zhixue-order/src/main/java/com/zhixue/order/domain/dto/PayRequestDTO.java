package com.zhixue.order.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 支付请求的数据传输对象。
 * 用于接收前端传来的支付请求参数。
 */
@Data
public class PayRequestDTO {

    /**
     * 订单编号，标识要支付哪个订单
     */
    @NotBlank(message = "订单编号不能为空")
    private String orderNo;

    /**
     * 用户编号，表示是谁在支付
     */
    @NotNull(message = "用户编号不能为空")
    private Long userId;

    /**
     * 支付渠道，alipay 表示支付宝，wechat 表示微信支付
     */
    @NotBlank(message = "支付渠道不能为空")
    private String payChannel;
}


