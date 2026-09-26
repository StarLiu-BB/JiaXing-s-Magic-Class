package com.zhixue.order.domain.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付结果消息的数据传输对象。
 * 用于在消息队列中传递支付结果信息。
 */
@Data
public class PayResultMessage {

    /**
     * 订单编号
     */
    private String orderNo;

    /**
     * 支付渠道，wechat 表示微信支付，alipay 表示支付宝
     */
    private String payChannel;

    /**
     * 第三方支付平台的订单号
     */
    private String payNo;

    /**
     * 支付状态，1 表示支付成功，2 表示支付失败
     */
    private Integer payStatus;

    /**
     * 支付完成时间
     */
    private LocalDateTime payTime;

    /**
     * 第三方回调的实付金额，用于与订单金额核对，防止少付冒充全额支付
     */
    private BigDecimal amount;

    /**
     * 回调签名，服务端用共享密钥重算并比对，防止伪造回调
     */
    private String sign;
}


