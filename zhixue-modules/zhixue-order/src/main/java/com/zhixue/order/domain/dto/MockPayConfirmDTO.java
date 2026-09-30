package com.zhixue.order.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 模拟收银台确认付款请求。
 */
@Data
public class MockPayConfirmDTO {

    @NotBlank(message = "订单编号不能为空")
    private String orderNo;
}
