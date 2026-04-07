package com.zhixue.marketing.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 优惠券状态变更请求。
 */
@Data
public class CouponStatusDTO {

    @NotNull(message = "状态不能为空")
    private Integer status;
}
