package com.zhixue.marketing.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixue.common.mybatis.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 优惠券实体类
 * 作用：存储优惠券的基本信息，比如名称、优惠金额、使用条件、有效期等。
 * 它是营销模块中管理优惠券的基础，所有优惠券相关的操作都基于这个类。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("coupon")
public class Coupon extends BaseEntity {

    /** 优惠券名称 */
    private String name;
    /** 优惠券总发行量 */
    private Integer totalCount;
    /** 优惠券剩余数量 */
    private Integer remainCount;
    /** 优惠金额（比如10元优惠券） */
    private BigDecimal discount;
    /** 使用门槛金额（比如满100元可用） */
    private BigDecimal thresholdAmount;
    /** 优惠券开始使用时间 */
    private LocalDateTime startTime;
    /** 优惠券结束使用时间 */
    private LocalDateTime endTime;
    /** 优惠券状态：0未开始 1生效 2结束 */
    private Integer status;
    /** 每人最多可以领取的数量 */
    private Integer userLimit;
}


