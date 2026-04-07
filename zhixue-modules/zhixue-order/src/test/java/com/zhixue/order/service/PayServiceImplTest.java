package com.zhixue.order.service;

import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.order.domain.dto.PayRequestDTO;
import com.zhixue.order.domain.dto.PayResponse;
import com.zhixue.order.domain.entity.Order;
import com.zhixue.order.service.impl.PayServiceImpl;
import com.zhixue.order.strategy.PayStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayServiceImplTest {

    @Mock
    private OrderService orderService;

    @Mock
    private PayStrategy alipayStrategy;

    @InjectMocks
    private PayServiceImpl payService;

    private PayRequestDTO request;
    private Order order;

    @BeforeEach
    void setUp() {
        request = new PayRequestDTO();
        request.setOrderNo("ORD123");
        request.setUserId(1001L);
        request.setPayChannel("alipay");

        order = new Order();
        order.setOrderNo("ORD123");
        order.setUserId(1001L);
        order.setStatus(0);
        order.setAmount(BigDecimal.valueOf(99.99));
    }

    @Test
    void shouldPaySuccessfullyWhenStrategyMatched() {
        payService = new PayServiceImpl(orderService, List.of(alipayStrategy));
        when(orderService.findByOrderNo("ORD123")).thenReturn(order);
        when(alipayStrategy.supports("alipay")).thenReturn(true);
        when(alipayStrategy.pay(order)).thenReturn(new PayResponse("https://sandbox-pay/order/ORD123"));

        PayResponse response = payService.pay(request);

        assertThat(response).isNotNull();
        assertThat(response.getPayUrl()).contains("ORD123");
    }

    @Test
    void shouldThrowWhenNoStrategyMatched() {
        payService = new PayServiceImpl(orderService, List.of(alipayStrategy));
        when(orderService.findByOrderNo("ORD123")).thenReturn(order);
        when(alipayStrategy.supports("alipay")).thenReturn(false);

        assertThatThrownBy(() -> payService.pay(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不支持的支付渠道");
    }
}
