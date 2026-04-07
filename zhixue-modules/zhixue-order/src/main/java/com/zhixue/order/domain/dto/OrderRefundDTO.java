package com.zhixue.order.domain.dto;

import lombok.Data;

/**
 * 退款请求。
 */
@Data
public class OrderRefundDTO {

    /**
     * 退款原因
     */
    private String reason;
}
