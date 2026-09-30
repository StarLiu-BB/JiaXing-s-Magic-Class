package com.zhixue.api.order.domain;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单摘要（服务间返回）。字段名与订单服务 Order 实体一致。
 */
@Data
public class OrderInfo {

    private String orderNo;
    private BigDecimal amount;
    /** 0待支付 1已支付 2已取消 3已退款 */
    private Integer status;
}
