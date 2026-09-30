package com.zhixue.order.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 支付响应的数据传输对象。
 * 用于返回支付结果给前端，包含支付链接或二维码。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayResponse {

    /**
     * 支付跳转链接或二维码地址，用户点击或扫码后可以完成支付
     */
    private String payUrl;

    /**
     * 支付渠道（mock / wechat / alipay）
     */
    private String payChannel;

    /**
     * 支付模式（mock 表示本地模拟收银台，不产生真实扣款）
     */
    private String mode;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 应付金额，供收银台展示（以服务端订单金额为准）
     */
    private BigDecimal amount;

    public PayResponse(String payUrl) {
        this.payUrl = payUrl;
    }
}

