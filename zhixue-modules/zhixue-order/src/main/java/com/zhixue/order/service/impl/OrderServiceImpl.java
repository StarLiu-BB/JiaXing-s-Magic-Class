package com.zhixue.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixue.api.course.RemoteCourseService;
import com.zhixue.api.course.domain.CourseInfo;
import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.domain.PageQuery;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.order.domain.dto.CreateOrderDTO;
import com.zhixue.order.domain.entity.Order;
import com.zhixue.order.mapper.OrderMapper;
import com.zhixue.order.service.OrderService;
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

    /** schema 约定：0=课程 1=其他。当前只售卖课程。 */
    private static final int PRODUCT_TYPE_COURSE = 0;
    private static final int STATUS_PENDING = 0;
    private static final int STATUS_PAID = 1;
    private static final int COURSE_PUBLISHED = 1;
    private static final int COURSE_ON_SHELF = 1;

    private final OrderMapper orderMapper;
    private final RabbitTemplate rabbitTemplate;
    private final RemoteCourseService remoteCourseService;

    @Value("${order.timeout-exchange:exchange_order_delay}")
    private String timeoutExchange;

    @Value("${order.timeout-routing-key:order.timeout}")
    private String timeoutRoutingKey;

    @Value("${order.timeout-delay-ms:1800000}")
    private int timeoutDelayMs;

    @Value("${zhixue.integration.order-mq.mode:${ZHIXUE_ORDER_MQ_MODE:sandbox}}")
    private String orderMqMode;

    // 本方法只操作本库并投递 MQ，没有跨服务调用，本地事务已足够。
    // 此处原为 @GlobalTransactional，但缺少 undo_log 表与 DataSourceProxy，
    // AT 模式根本无法回滚 —— 那是比不用分布式事务更危险的假象。
    // 未来若引入跨服务扣减（库存/积分），需同时补齐 undo_log 才能改回全局事务。
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order createOrder(Long userId, CreateOrderDTO dto) {
        BigDecimal price = resolveCoursePrice(dto.getProductId());

        List<Order> activeOrders = findActiveOrders(userId, dto.getProductId());
        if (activeOrders.stream().anyMatch(o -> Integer.valueOf(STATUS_PAID).equals(o.getStatus()))) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "您已购买该课程，无需重复下单");
        }
        Order reusable = activeOrders.stream()
                .filter(o -> Integer.valueOf(STATUS_PENDING).equals(o.getStatus()) && !isExpired(o))
                .findFirst()
                .orElse(null);
        if (reusable != null) {
            log.info("复用未过期的待支付订单 orderNo={}, userId={}", reusable.getOrderNo(), userId);
            return reusable;
        }

        Order order = buildDraftOrder(userId, dto.getProductId(), price);
        int insert = orderMapper.insert(order);
        if (insert <= 0) {
            throw new ServiceException("创建订单失败");
        }

        try {
            if (!"real".equalsIgnoreCase(orderMqMode)) {
                log.info("sandbox 模式跳过订单超时延时消息 orderNo={}", order.getOrderNo());
                return order;
            }
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
    public Order previewOrder(Long userId, CreateOrderDTO dto) {
        return buildDraftOrder(userId, dto.getProductId(), resolveCoursePrice(dto.getProductId()));
    }

    @Override
    public boolean hasPaid(Long userId, Long productId) {
        LambdaQueryWrapper<Order> qw = new LambdaQueryWrapper<>();
        qw.eq(Order::getUserId, userId)
                .eq(Order::getProductId, productId)
                .eq(Order::getStatus, STATUS_PAID);
        return orderMapper.selectCount(qw) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void paySuccess(String orderNo, String payChannel, String payNo) {
        // 用带 status 条件的原子更新实现幂等：重复回调只有第一次能影响到行。
        // 不可改回"先查后改"，否则并发回调会重复置为已支付。
        LambdaUpdateWrapper<Order> uw = new LambdaUpdateWrapper<>();
        uw.eq(Order::getOrderNo, orderNo)
                .eq(Order::getStatus, 0)
                .set(Order::getStatus, 1)
                .set(Order::getPayChannel, payChannel)
                .set(Order::getPayNo, payNo)
                .set(Order::getPayTime, LocalDateTime.now());

        int affected = orderMapper.update(null, uw);
        if (affected == 0) {
            log.info("订单无需处理（不存在或已非待支付状态），跳过 orderNo={}", orderNo);
            return;
        }
        log.info("订单支付成功 orderNo={}, payChannel={}", orderNo, payChannel);
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
        // 观看权限以订单状态为准，退款即收回。与支付一样用条件更新保证原子：
        // 原 updateById 无条件覆盖，并发下可能把状态已变更的订单改成已退款
        LambdaUpdateWrapper<Order> uw = new LambdaUpdateWrapper<>();
        uw.eq(Order::getId, id)
                .eq(Order::getStatus, STATUS_PAID)
                .set(Order::getStatus, 3)
                .set(Order::getRemark, StringUtils.hasText(reason) ? reason : "运营审核退款");
        if (orderMapper.update(null, uw) == 0) {
            throw new ServiceException("仅已支付订单可退款");
        }
        log.info("订单已退款，课程观看权限随之失效 orderNo={}", order.getOrderNo());
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

    private Order buildDraftOrder(Long userId, Long productId, BigDecimal price) {
        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setProductId(productId);
        order.setProductType(PRODUCT_TYPE_COURSE);
        order.setAmount(price);
        order.setStatus(STATUS_PENDING);
        order.setExpireTime(LocalDateTime.now().plusMinutes(30));
        return order;
    }

    /**
     * 向课程服务核价。取不到真实价格时一律拒绝下单（fail-closed），
     * 绝不退回信任客户端传入的金额。
     */
    private BigDecimal resolveCoursePrice(Long courseId) {
        R<CourseInfo> result;
        try {
            result = remoteCourseService.getCourse(courseId);
        } catch (Exception e) {
            log.error("核价时调用课程服务失败 courseId={}", courseId, e);
            throw new ServiceException(HttpStatus.SERVICE_UNAVAILABLE, "课程服务繁忙，暂不可购买，请稍后重试");
        }
        CourseInfo course = result != null && result.isSuccess() ? result.getData() : null;
        if (course == null) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "课程不存在或暂不可购买");
        }
        if (!Integer.valueOf(COURSE_PUBLISHED).equals(course.getStatus())
                || !Integer.valueOf(COURSE_ON_SHELF).equals(course.getShelfStatus())) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "课程未上架，暂不可购买");
        }
        if (course.getPrice() == null || course.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "免费课程无需购买，可直接学习");
        }
        return course.getPrice();
    }

    /** 该用户对该商品的待支付与已支付订单。 */
    private List<Order> findActiveOrders(Long userId, Long productId) {
        LambdaQueryWrapper<Order> qw = new LambdaQueryWrapper<>();
        qw.eq(Order::getUserId, userId)
                .eq(Order::getProductId, productId)
                .in(Order::getStatus, STATUS_PENDING, STATUS_PAID);
        return orderMapper.selectList(qw);
    }

    private boolean isExpired(Order order) {
        return order.getExpireTime() == null || !order.getExpireTime().isAfter(LocalDateTime.now());
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
        // 同样用条件更新，消除与 OrderTimeoutConsumer 并发取消时的竞态
        LambdaUpdateWrapper<Order> uw = new LambdaUpdateWrapper<>();
        uw.eq(Order::getOrderNo, order.getOrderNo())
                .eq(Order::getStatus, 0)
                .set(Order::getStatus, 2)
                .set(Order::getRemark, StringUtils.hasText(reason) ? reason : "用户取消订单");

        int affected = orderMapper.update(null, uw);
        if (affected == 0) {
            log.info("订单状态已变更，跳过取消 orderNo={}", order.getOrderNo());
            if (strict) {
                throw new ServiceException("当前订单状态不可取消");
            }
        }
    }

    private LambdaQueryWrapper<Order> rangeWrapper(LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(startTime != null, Order::getCreateTime, startTime)
                .le(endTime != null, Order::getCreateTime, endTime);
        return wrapper;
    }
}
