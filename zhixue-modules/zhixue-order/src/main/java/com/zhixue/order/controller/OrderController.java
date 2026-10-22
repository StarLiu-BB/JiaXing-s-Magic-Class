package com.zhixue.order.controller;

import com.zhixue.api.order.domain.SeckillOrderRequest;
import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.domain.PageQuery;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.security.annotation.RequireLogin;
import com.zhixue.common.security.annotation.RequirePermission;
import com.zhixue.common.security.utils.SecurityUtils;
import com.zhixue.order.domain.dto.CreateOrderDTO;
import com.zhixue.order.domain.dto.OrderCancelDTO;
import com.zhixue.order.domain.dto.OrderRefundDTO;
import com.zhixue.order.domain.entity.Order;
import com.zhixue.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;

/**
 * 订单管理接口。
 */
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    /** 运营查看全站订单 */
    static final String PERM_LIST = "order:list";
    /** 运营取消任意待支付订单 */
    static final String PERM_CANCEL = "order:cancel";
    /** 退款只允许运营审核后执行，学生不能自助退款 */
    static final String PERM_REFUND = "order:refund";

    private final OrderService orderService;

    @RequireLogin
    @PostMapping("/preview")
    public R<Order> preview(@Valid @RequestBody CreateOrderDTO dto) {
        return R.ok(orderService.previewOrder(SecurityUtils.requireUserId(), dto));
    }

    @RequireLogin
    @PostMapping
    public R<Order> createAlias(@Valid @RequestBody CreateOrderDTO dto) {
        return R.ok(orderService.createOrder(SecurityUtils.requireUserId(), dto));
    }

    @RequireLogin
    @PostMapping("/create")
    public R<Order> create(@Valid @RequestBody CreateOrderDTO dto) {
        return R.ok(orderService.createOrder(SecurityUtils.requireUserId(), dto));
    }

    @RequireLogin
    @GetMapping("/detail")
    public R<Order> detail(@RequestParam String orderNo) {
        return R.ok(requireAccessible(orderService.findByOrderNo(orderNo), PERM_LIST));
    }

    @RequireLogin
    @GetMapping("/{id}")
    public R<Order> getById(@PathVariable Long id) {
        return R.ok(requireAccessible(orderService.findById(id), PERM_LIST));
    }

    /**
     * 运营（order:list）可按任意 userId 查询；其余用户一律只能看自己的订单，
     * 传入的 userId 被忽略 —— 否则不传 userId 就能拉取全站订单。
     */
    @RequireLogin
    @GetMapping("/list")
    public R<PageResult<Order>> list(PageQuery pageQuery,
                                     @RequestParam(required = false) String orderNo,
                                     @RequestParam(required = false) Long userId,
                                     @RequestParam(required = false) Integer status) {
        Long scopedUserId = SecurityUtils.hasAnyPermission(PERM_LIST) ? userId : SecurityUtils.requireUserId();
        return R.ok(orderService.pageOrders(pageQuery, orderNo, scopedUserId, status));
    }

    @RequireLogin
    @PutMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id,
                          @RequestBody(required = false) OrderCancelDTO dto) {
        requireAccessible(orderService.findById(id), PERM_CANCEL);
        orderService.cancelOrderById(id, dto != null ? dto.getReason() : null);
        return R.ok();
    }

    /**
     * 服务间接口：用户是否已购买该课程。课程服务据此决定是否下发付费课时视频地址。
     * 订单表是购买关系的唯一事实来源 —— 支付即可看、退款即失效，无需跨服务同步。
     * 网关从不设置 X-Inner-Call，外部请求（包括已登录用户）一律拒绝。
     */
    @GetMapping("/inner/purchased")
    public R<Boolean> purchased(@RequestParam Long userId, @RequestParam Long productId) {
        if (!SecurityUtils.isTrustedInternalCall()) {
            throw new ServiceException(HttpStatus.FORBIDDEN, "禁止外部访问内部订单接口");
        }
        return R.ok(orderService.hasPaid(userId, productId));
    }

    /**
     * 服务间接口：秒杀成交后按秒杀价生成待支付订单。
     * 价格由调用方决定，因此必须仅限可信内部调用，否则任何人都能以任意价格给自己下单。
     */
    @PostMapping("/inner/seckill")
    public R<Order> createSeckillOrder(@Valid @RequestBody SeckillOrderRequest request) {
        if (!SecurityUtils.isTrustedInternalCall()) {
            throw new ServiceException(HttpStatus.FORBIDDEN, "禁止外部访问内部订单接口");
        }
        return R.ok(orderService.createSeckillOrder(
                request.getUserId(), request.getProductId(), request.getAmount(), request.getActivityId()));
    }

    /** 按活动和用户确认原秒杀订单，仅限服务间调用。 */
    @GetMapping("/inner/seckill")
    public R<Order> findSeckillOrder(@RequestParam Long activityId, @RequestParam Long userId) {
        if (!SecurityUtils.isTrustedInternalCall()) {
            throw new ServiceException(HttpStatus.FORBIDDEN, "禁止外部访问内部订单接口");
        }
        return R.ok(orderService.findSeckillOrder(activityId, userId));
    }

    @RequirePermission(PERM_REFUND)
    @PutMapping("/{id}/refund")
    public R<Void> refund(@PathVariable Long id,
                          @RequestBody(required = false) OrderRefundDTO dto) {
        orderService.refundOrder(id, dto != null ? dto.getReason() : null);
        return R.ok();
    }

    /**
     * 本人订单或具备指定运营权限才可访问。对他人订单统一返回"订单不存在"，
     * 而不是 403 —— 避免攻击者借响应差异枚举有效的订单 ID/订单号。
     */
    private Order requireAccessible(Order order, String managePermission) {
        if (order == null
                || !(order.getUserId().equals(SecurityUtils.requireUserId())
                || SecurityUtils.hasAnyPermission(managePermission))) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        return order;
    }

    @RequirePermission(PERM_LIST)
    @GetMapping("/statistics")
    public R<Map<String, Object>> statistics(@RequestParam(required = false) String startTime,
                                             @RequestParam(required = false) String endTime) {
        return R.ok(orderService.statistics(parseDateTime(startTime).orElse(null), parseDateTime(endTime).orElse(null)));
    }

    private Optional<LocalDateTime> parseDateTime(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDateTime.parse(input, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Optional.of(LocalDateTime.parse(input, DateTimeFormatter.ISO_DATE_TIME));
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Optional.of(LocalDate.parse(input, DateTimeFormatter.ISO_DATE).atStartOfDay());
        } catch (DateTimeParseException ignored) {
        }
        return Optional.empty();
    }
}
