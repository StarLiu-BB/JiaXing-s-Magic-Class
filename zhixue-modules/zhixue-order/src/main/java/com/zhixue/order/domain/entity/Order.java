package com.zhixue.order.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixue.common.mybatis.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体类。
 * 这个类对应数据库中的订单表，存储订单的基本信息，包括订单号、用户、商品、金额、支付状态等。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("`order`")
public class Order extends BaseEntity {

    /**
     * 订单编号，唯一标识一个订单
     */
    private String orderNo;

    /**
     * 下单用户的编号
     */
    private Long userId;

    /**
     * 商品编号
     */
    private Long productId;

    /**
     * 商品类型，比如课程、会员等
     */
    private Integer productType;

    /**
     * 订单金额
     */
    private BigDecimal amount;

    /**
     * 订单状态，0表示待支付，1表示已支付，2表示已取消，3表示已退款
     */
    private Integer status;

    /**
     * 支付渠道，wechat 表示微信支付，alipay 表示支付宝
     */
    private String payChannel;

    /**
     * 第三方支付平台的订单号
     */
    private String payNo;

    /**
     * 支付完成的时间
     */
    private LocalDateTime payTime;

    /**
     * 订单过期时间，超过这个时间未支付会自动取消
     */
    private LocalDateTime expireTime;

    /**
     * 备注信息，比如取消原因
     */
    private String remark;
}


