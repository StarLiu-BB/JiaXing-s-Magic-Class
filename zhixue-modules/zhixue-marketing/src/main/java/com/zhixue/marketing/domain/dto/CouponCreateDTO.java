package com.zhixue.marketing.domain.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 创建优惠券参数DTO
 * 作用：封装创建优惠券活动时需要传递的参数，包括优惠券的基本信息、使用规则和有效期等。
 * 这个类用于前端和后端之间传递优惠券创建请求数据，确保数据的完整性和验证。
 */
@Data
public class CouponCreateDTO {

    /**
     * 优惠券名称
     * 说明：优惠券的显示名称，不能为空
     */
    @NotBlank(message = "名称不能为空")
    private String name;

    /**
     * 优惠券总数量
     * 说明：此次活动可发放的优惠券总数，必须大于0
     */
    @NotNull
    @Min(1)
    private Integer totalCount;

    /**
     * 优惠金额
     * 说明：优惠券可以抵扣的金额，必须大于0
     */
    @NotNull
    private BigDecimal discount;

    /**
     * 使用门槛金额
     * 说明：订单金额必须达到这个门槛才能使用优惠券，例如满100元可用
     */
    @NotNull
    private BigDecimal thresholdAmount;

    /**
     * 活动开始时间
     * 说明：优惠券开始生效的时间
     */
    @NotNull
    private LocalDateTime startTime;

    /**
     * 活动结束时间
     * 说明：优惠券停止生效的时间，必须是未来的时间
     */
    @NotNull
    @Future
    private LocalDateTime endTime;

    /**
     * 每人限领数量
     * 说明：每个用户最多可以领取的优惠券数量，必须大于0
     */
    @NotNull
    @Min(1)
    private Integer userLimit;
}


