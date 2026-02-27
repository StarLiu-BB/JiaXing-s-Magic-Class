package com.zhixue.order.service;

import com.zhixue.order.domain.dto.CreateOrderDTO;
import com.zhixue.order.domain.entity.Order;

/**
 * 订单服务接口。
 * 定义了订单相关的业务操作，包括创建订单、支付成功回调、取消订单、查询订单等。
 */
public interface OrderService {

    /**
     * 创建订单。
     * 这个方法是分布式事务的入口，会同时创建订单记录和发送延时消息用于超时取消。
     * @param dto 创建订单需要的参数
     * @return 创建成功的订单信息
     */
    Order createOrder(CreateOrderDTO dto);

    /**
     * 支付成功回调处理。
     * 收到支付成功的通知后，更新订单状态为已支付，记录支付渠道和支付单号。
     * @param orderNo 订单编号
     * @param payChannel 支付渠道，微信或支付宝
     * @param payNo 第三方支付平台的订单号
     */
    void paySuccess(String orderNo, String payChannel, String payNo);

    /**
     * 取消订单。
     * 将订单状态更新为已取消，并记录取消原因。
     * @param orderNo 订单编号
     * @param reason 取消原因
     */
    void cancelOrder(String orderNo, String reason);

    /**
     * 根据订单号查询订单信息。
     * @param orderNo 订单编号
     * @return 订单信息
     */
    Order findByOrderNo(String orderNo);
}


