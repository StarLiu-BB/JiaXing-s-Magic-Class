package com.zhixue.marketing.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixue.common.mybatis.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 秒杀订单实体类
 * 作用：存储用户参与秒杀活动的订单信息，记录哪个用户在哪个秒杀活动中下了订单。
 * 它是营销模块中秒杀功能的重要组成部分，用于跟踪秒杀订单的状态和处理。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("seckill_order")
public class SeckillOrder extends BaseEntity {

    /** 秒杀活动ID（关联到具体的秒杀活动） */
    private Long activityId;
    /** 参与秒杀的用户ID */
    private Long userId;
    /** 秒杀订单号（唯一标识一个秒杀订单） */
    private String orderNo;
    /** 订单状态：0已锁定 1已下单 2已取消 */
    private Integer status;
}


