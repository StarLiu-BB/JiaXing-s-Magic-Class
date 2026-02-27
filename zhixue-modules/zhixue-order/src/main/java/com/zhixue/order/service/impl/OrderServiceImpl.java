package com.zhixue.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.order.domain.dto.CreateOrderDTO;
import com.zhixue.order.domain.entity.Order;
import com.zhixue.order.mapper.OrderMapper;
import com.zhixue.order.service.OrderService;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单服务实现类。
 * 负责处理订单相关的业务逻辑，包括创建订单、支付成功回调、取消订单、查询订单等操作。
 * 创建订单时使用分布式事务保证数据一致性，同时发送延时消息用于超时自动取消订单。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final RabbitTemplate rabbitTemplate;

    /**
     * 订单超时交换机。
     */
    @Value("${order.timeout-exchange:exchange_order_delay}")
    private String timeoutExchange;

    /**
     * 订单超时路由键。
     */
    @Value("${order.timeout-routing-key:order.timeout}")
    private String timeoutRoutingKey;

    /**
     * 订单超时时间（毫秒），默认30分钟。
     */
    @Value("${order.timeout-delay-ms:1800000}")
    private int timeoutDelayMs;

    /**
     * 创建订单。
     * 这个方法使用分布式事务，保证订单创建和消息发送的一致性。
     * 订单创建成功后，会发送一条延时消息，如果30分钟内没有支付，订单会自动取消。
     * @param dto 创建订单需要的参数
     * @return 创建成功的订单信息
     */
    @Override
    @GlobalTransactional(rollbackFor = Exception.class)
    public Order createOrder(CreateOrderDTO dto) {
        if (dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("金额必须大于0");
        }
        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(dto.getUserId());
        order.setProductId(dto.getProductId());
        order.setProductType(dto.getProductType());
        order.setAmount(dto.getAmount());
        order.setStatus(0);
        order.setExpireTime(LocalDateTime.now().plusMinutes(30));

        int insert = orderMapper.insert(order);
        if (insert <= 0) {
            throw new ServiceException("创建订单失败");
        }

        // 发送延时消息用于超时取消
        try {
            rabbitTemplate.convertAndSend(timeoutExchange, timeoutRoutingKey, order.getOrderNo(), msg -> {
                msg.getMessageProperties().setDelay(timeoutDelayMs);
                return msg;
            });
        } catch (Exception e) {
            log.error("发送订单超时延时消息失败 orderNo={}", order.getOrderNo(), e);
        }
        return order;
    }

    /**
     * 支付成功回调处理。
     * 收到支付成功的通知后，更新订单状态为已支付，记录支付渠道和支付单号。
     * 如果订单已经处理过（状态不是待支付），则跳过处理。
     * @param orderNo 订单编号
     * @param payChannel 支付渠道，微信或支付宝
     * @param payNo 第三方支付平台的订单号
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void paySuccess(String orderNo, String payChannel, String payNo) {
        Order order = findByOrderNo(orderNo);
        if (order == null) {
            log.warn("支付回调未找到订单 orderNo={}", orderNo);
            return;
        }
        if (order.getStatus() != 0) {
            log.info("订单已处理，跳过 orderNo={}, status={}", orderNo, order.getStatus());
            return;
        }
        order.setStatus(1);
        order.setPayChannel(payChannel);
        order.setPayNo(payNo);
        order.setPayTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    /**
     * 取消订单。
     * 将订单状态更新为已取消，并记录取消原因。
     * 只有待支付状态的订单才能被取消。
     * @param orderNo 订单编号
     * @param reason 取消原因
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(String orderNo, String reason) {
        Order order = findByOrderNo(orderNo);
        if (order == null) {
            return;
        }
        if (order.getStatus() != 0) {
            return;
        }
        order.setStatus(2);
        order.setRemark(reason);
        orderMapper.updateById(order);
    }

    /**
     * 根据订单号查询订单信息。
     * @param orderNo 订单编号
     * @return 订单信息
     */
    @Override
    public Order findByOrderNo(String orderNo) {
        LambdaQueryWrapper<Order> qw = new LambdaQueryWrapper<>();
        qw.eq(Order::getOrderNo, orderNo);
        return orderMapper.selectOne(qw);
    }

    /**
     * 生成订单编号。
     * 格式为：ORD + 当前时间戳 + 4位随机数。
     * @return 订单编号
     */
    private String generateOrderNo() {
        return "ORD" + System.currentTimeMillis() + RandomUtils.nextInt(1000, 9999);
    }
}


