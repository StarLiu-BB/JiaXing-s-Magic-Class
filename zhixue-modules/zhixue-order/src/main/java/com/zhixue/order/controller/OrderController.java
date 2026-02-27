package com.zhixue.order.controller;

import com.zhixue.common.core.domain.R;
import com.zhixue.order.domain.dto.CreateOrderDTO;
import com.zhixue.order.domain.entity.Order;
import com.zhixue.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单管理接口。
 * 这个类负责处理前端发来的关于订单的所有请求，包括创建订单、查询订单详情等操作。
 */
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 创建一个新订单。
     * @param dto 创建订单需要的参数，包括商品信息、用户信息、金额等
     * @return 创建成功的订单信息
     */
    @PostMapping("/create")
    public R<Order> create(@Valid @RequestBody CreateOrderDTO dto) {
        return R.ok(orderService.createOrder(dto));
    }

    /**
     * 根据订单号查询订单详情。
     * @param orderNo 订单编号
     * @return 订单详情信息
     */
    @GetMapping("/detail")
    public R<Order> detail(@RequestParam String orderNo) {
        return R.ok(orderService.findByOrderNo(orderNo));
    }
}


