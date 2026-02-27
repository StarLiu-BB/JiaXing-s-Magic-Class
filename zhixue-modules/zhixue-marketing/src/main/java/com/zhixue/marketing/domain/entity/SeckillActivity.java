package com.zhixue.marketing.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixue.common.mybatis.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀活动实体类
 * 作用：存储秒杀活动的基本信息，比如商品ID、秒杀价格、库存和活动时间等。
 * 它是营销模块中管理秒杀活动的基础，所有秒杀相关的操作都基于这个类。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("seckill_activity")
public class SeckillActivity extends BaseEntity {

    /** 秒杀活动标题（比如："双11限时秒杀"） */
    private String title;
    /** 参与秒杀的商品ID */
    private Long productId;
    /** 秒杀价格（活动期间的优惠价格） */
    private BigDecimal seckillPrice;
    /** 秒杀总库存数量 */
    private Integer totalStock;
    /** 秒杀活动开始时间 */
    private LocalDateTime startTime;
    /** 秒杀活动结束时间 */
    private LocalDateTime endTime;
    /** 秒杀活动状态：0未开始 1进行中 2已结束 */
    private Integer status;
}


