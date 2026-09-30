package com.zhixue.marketing.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 秒杀成交结果：订单服务中的真实待支付订单，客户端可直接进入收银台。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillResultVO {

    /** 订单服务的订单号 */
    private String orderNo;

    /** 应付金额（秒杀价） */
    private BigDecimal amount;
}
