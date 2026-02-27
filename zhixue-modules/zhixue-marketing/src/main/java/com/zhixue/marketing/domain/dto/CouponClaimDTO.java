package com.zhixue.marketing.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 领取优惠券参数DTO
 * 作用：封装用户领取优惠券时需要传递的参数，包括优惠券ID和用户ID。
 * 这个类用于前端和后端之间传递优惠券领取请求数据，确保数据的完整性和验证。
 */
@Data
public class CouponClaimDTO {

    /**
     * 优惠券ID
     * 说明：要领取的优惠券的唯一标识，不能为空
     */
    @NotNull
    private Long couponId;

    /**
     * 用户ID
     * 说明：领取优惠券的用户的唯一标识，不能为空
     */
    @NotNull
    private Long userId;
}


