package com.zhixue.order.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建订单的数据传输对象。
 * 用于接收前端传来的创建订单请求参数。
 */
@Data
public class CreateOrderDTO {

    /**
     * 用户编号，表示是谁下的订单
     */
    @NotNull(message = "用户编号不能为空")
    private Long userId;

    /**
     * 商品编号，表示买的是什么
     */
    @NotNull(message = "商品编号不能为空")
    private Long productId;

    /**
     * 商品类型，比如课程、会员等
     */
    @NotNull(message = "商品类型不能为空")
    private Integer productType;

    /**
     * 订单金额，必须大于0
     */
    @NotNull(message = "金额不能为空")
    @Positive(message = "金额必须大于0")
    private BigDecimal amount;
}


