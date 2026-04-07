package com.zhixue.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.domain.PageQuery;
import com.zhixue.common.core.domain.PageResult;
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
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单服务实现类。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final RabbitTemplate rabbitTemplate;

    @Value("${order.timeout-exchange:exchange_order_delay}")
    private String timeoutExchange;

    @Value("${order.timeout-routing-key:order.timeout}")
    private String timeoutRoutingKey;

    @Value("${order.timeout-delay-ms:1800000}")
    private int timeoutDelayMs;

    @Override
    @GlobalTransactional(rollbackFor = Exception.class)
    public Order createOrder(CreateOrderDTO dto) {
        Order order = buildDraftOrder(dto);
        int insert = orderMapper.insert(order);
        if (insert <= 0) {
            throw new ServiceException("创建订单失败");
        }

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

    @Override
    public Order previewOrder(CreateOrderDTO dto) {
        return buildDraftOrder(dto);
    }

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(String orderNo, String reason) {
        Order order = findByOrderNo(orderNo);
        cancelPendingOrder(order, reason, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrderById(Long id, String reason) {
        Order order = findById(id);
        cancelPendingOrder(order, reason, true);
    }

    @Override
    public Order findByOrderNo(String orderNo) {
        LambdaQueryWrapper<Order> qw = new LambdaQueryWrapper<>();
        qw.eq(Order::getOrderNo, orderNo);
        return orderMapper.selectOne(qw);
    }

    @Override
    public Order findById(Long id) {
        return orderMapper.selectById(id);
    }

    @Override
    public PageResult<Order> pageOrders(PageQuery query, String orderNo, Long userId, Integer status) {
        Page<Order> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<Order> qw = new LambdaQueryWrapper<>();
        qw.eq(userId != null, Order::getUserId, userId)
                .eq(status != null, Order::getStatus, status)
                .eq(StringUtils.hasText(orderNo), Order::getOrderNo, orderNo)
                .orderByDesc(Order::getCreateTime);
        Page<Order> result = orderMapper.selectPage(page, qw);
        return PageResult.of(result.getRecords(), result.getTotal(), query.getPageSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refundOrder(Long id, String reason) {
        Order order = findById(id);
        if (order == null) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        if (order.getStatus() == 3) {
            return;
        }
        if (order.getStatus() != 1) {
            throw new ServiceException("仅已支付订单可退款");
        }
        order.setStatus(3);
        order.setRemark(StringUtils.hasText(reason) ? reason : "用户发起退款");
        orderMapper.updateById(order);
    }

    @Override
    public Map<String, Object> statistics(LocalDateTime startTime, LocalDateTime endTime) {
        long totalCount = orderMapper.selectCount(rangeWrapper(startTime, endTime));
        long pendingCount = orderMapper.selectCount(rangeWrapper(startTime, endTime).eq(Order::getStatus, 0));
        long paidCount = orderMapper.selectCount(rangeWrapper(startTime, endTime).eq(Order::getStatus, 1));
        long canceledCount = orderMapper.selectCount(rangeWrapper(startTime, endTime).eq(Order::getStatus, 2));
        long refundedCount = orderMapper.selectCount(rangeWrapper(startTime, endTime).eq(Order::getStatus, 3));
        List<Order> paidOrders = orderMapper.selectList(rangeWrapper(startTime, endTime).eq(Order::getStatus, 1));
        BigDecimal paidAmount = paidOrders.stream()
                .map(Order::getAmount)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCount", totalCount);
        result.put("pendingCount", pendingCount);
        result.put("paidCount", paidCount);
        result.put("canceledCount", canceledCount);
        result.put("refundedCount", refundedCount);
        result.put("paidAmount", paidAmount);
        return result;
    }

    private String generateOrderNo() {
        return "ORD" + System.currentTimeMillis() + RandomUtils.nextInt(1000, 9999);
    }

    private Order buildDraftOrder(CreateOrderDTO dto) {
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
        return order;
    }

    private void cancelPendingOrder(Order order, String reason, boolean strict) {
        if (order == null) {
            if (strict) {
                throw new ServiceException(HttpStatus.NOT_FOUND, "订单不存在");
            }
            return;
        }
        if (order.getStatus() != 0) {
            if (strict) {
                throw new ServiceException("当前订单状态不可取消");
            }
            return;
        }
        order.setStatus(2);
        order.setRemark(StringUtils.hasText(reason) ? reason : "用户取消订单");
        orderMapper.updateById(order);
    }

    private LambdaQueryWrapper<Order> rangeWrapper(LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(startTime != null, Order::getCreateTime, startTime)
                .le(endTime != null, Order::getCreateTime, endTime);
        return wrapper;
    }
}
