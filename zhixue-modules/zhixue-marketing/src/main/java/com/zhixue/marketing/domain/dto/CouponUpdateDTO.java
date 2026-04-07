package com.zhixue.marketing.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券更新请求。
 */
@Data
public class CouponUpdateDTO {

    @NotNull(message = "优惠券ID不能为空")
    private Long id;

    @NotBlank(message = "名称不能为空")
    private String name;

    @NotNull(message = "总数量不能为空")
    @Min(value = 1, message = "总数量必须大于0")
    private Integer totalCount;

    @NotNull(message = "优惠金额不能为空")
    private BigDecimal discount;

    @NotNull(message = "门槛金额不能为空")
    private BigDecimal thresholdAmount;

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    @NotNull(message = "状态不能为空")
    private Integer status;

    @NotNull(message = "每人限领不能为空")
    @Min(value = 1, message = "每人限领必须大于0")
    private Integer userLimit;
}
