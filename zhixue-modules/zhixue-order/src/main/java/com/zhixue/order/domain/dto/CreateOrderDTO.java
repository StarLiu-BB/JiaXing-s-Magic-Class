package com.zhixue.order.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建订单的数据传输对象。
 *
 * <p>只接收"买什么"。下单人取自登录态，金额由服务端向课程服务核价，
 * 商品类型固定为课程 —— 这三项都不得由客户端决定。
 * 客户端即使仍携带 userId / amount / productType，也会被忽略。</p>
 */
@Data
public class CreateOrderDTO {

    /**
     * 商品编号（课程 ID）
     */
    @NotNull(message = "商品编号不能为空")
    private Long productId;
}
