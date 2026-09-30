package com.zhixue.order.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 支付请求的数据传输对象。
 * 付款人取自登录态，不接收客户端传入的 userId。
 */
@Data
public class PayRequestDTO {

    /**
     * 订单编号，标识要支付哪个订单
     */
    @NotBlank(message = "订单编号不能为空")
    private String orderNo;

    /**
     * 支付渠道，alipay 表示支付宝，wechat 表示微信支付
     */
    @NotBlank(message = "支付渠道不能为空")
    private String payChannel;
}
