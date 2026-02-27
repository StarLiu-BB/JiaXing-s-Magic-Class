package com.zhixue.order.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}


