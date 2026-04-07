package com.zhixue.order.domain.dto;

import lombok.Data;

/**
 * 取消订单请求。
 */
@Data
public class OrderCancelDTO {

    /**
     * 取消原因
     */
    private String reason;
}
