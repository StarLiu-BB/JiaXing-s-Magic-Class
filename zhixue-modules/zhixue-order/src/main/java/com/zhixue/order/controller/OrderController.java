package com.zhixue.order.controller;

import com.zhixue.common.core.domain.PageQuery;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.domain.R;
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

    private final OrderService orderService;

    @PostMapping("/preview")
    public R<Order> preview(@Valid @RequestBody CreateOrderDTO dto) {
        return R.ok(orderService.previewOrder(dto));
    }

    @PostMapping
    public R<Order> createAlias(@Valid @RequestBody CreateOrderDTO dto) {
        return R.ok(orderService.createOrder(dto));
    }

    @PostMapping("/create")
    public R<Order> create(@Valid @RequestBody CreateOrderDTO dto) {
        return R.ok(orderService.createOrder(dto));
    }

    @GetMapping("/detail")
    public R<Order> detail(@RequestParam String orderNo) {
        return R.ok(orderService.findByOrderNo(orderNo));
    }

    @GetMapping("/{id}")
    public R<Order> getById(@PathVariable Long id) {
        return R.ok(orderService.findById(id));
    }

    @GetMapping("/list")
    public R<PageResult<Order>> list(PageQuery pageQuery,
                                     @RequestParam(required = false) String orderNo,
                                     @RequestParam(required = false) Long userId,
                                     @RequestParam(required = false) Integer status) {
        return R.ok(orderService.pageOrders(pageQuery, orderNo, userId, status));
    }

    @PutMapping("/{id}/cancel")
    public R<Void> cancel(@PathVariable Long id,
                          @RequestBody(required = false) OrderCancelDTO dto) {
        orderService.cancelOrderById(id, dto != null ? dto.getReason() : null);
        return R.ok();
    }

    @PutMapping("/{id}/refund")
    public R<Void> refund(@PathVariable Long id,
                          @RequestBody(required = false) OrderRefundDTO dto) {
        orderService.refundOrder(id, dto != null ? dto.getReason() : null);
        return R.ok();
    }

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
