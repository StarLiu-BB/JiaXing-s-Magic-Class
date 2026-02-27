package com.zhixue.marketing.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixue.common.mybatis.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户领取的优惠券记录类
 * 作用：记录每个用户领取了哪些优惠券，以及这些优惠券的使用状态（未使用、已使用、已过期）。
 * 它是连接优惠券和用户的桥梁，用于跟踪每个用户的优惠券使用情况。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("coupon_user")
public class CouponUser extends BaseEntity {

    /** 用户领取的优惠券ID */
    private Long couponId;
    /** 领取优惠券的用户ID */
    private Long userId;
    /** 优惠券使用状态：0未使用 1已使用 2已过期 */
    private Integer status;
    /** 优惠券过期时间 */
    private LocalDateTime expireTime;
}


