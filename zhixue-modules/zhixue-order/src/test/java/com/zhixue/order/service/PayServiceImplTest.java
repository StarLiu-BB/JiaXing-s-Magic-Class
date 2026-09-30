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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayServiceImplTest {

    private static final long OWNER = 1003L;

    @Mock
    private OrderService orderService;

    @Mock
    private PayStrategy alipayStrategy;

    private PayServiceImpl payService;
    private PayRequestDTO request;
    private Order order;

    @BeforeEach
    void setUp() {
        payService = new PayServiceImpl(orderService, List.of(alipayStrategy));

        request = new PayRequestDTO();
        request.setOrderNo("ORD123");
        request.setPayChannel("alipay");

        order = new Order();
        order.setOrderNo("ORD123");
        order.setUserId(OWNER);
        order.setProductId(2001L);
        order.setStatus(0);
        order.setAmount(BigDecimal.valueOf(99.99));
        order.setExpireTime(LocalDateTime.now().plusMinutes(10));
    }

    @Test
    void shouldPaySuccessfullyWhenStrategyMatched() {
        when(orderService.findByOrderNo("ORD123")).thenReturn(order);
        when(alipayStrategy.supports("alipay")).thenReturn(true);
        when(alipayStrategy.pay(order)).thenReturn(new PayResponse("https://sandbox-pay/order/ORD123"));

        PayResponse response = payService.pay(OWNER, request);

        assertThat(response.getPayUrl()).contains("ORD123");
    }

    @Test
    void shouldThrowWhenNoStrategyMatched() {
        when(orderService.findByOrderNo("ORD123")).thenReturn(order);
        when(alipayStrategy.supports("alipay")).thenReturn(false);

        assertThatThrownBy(() -> payService.pay(OWNER, request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不支持的支付渠道");
    }

    @Test
    void shouldRejectPayingOthersOrder() {
        when(orderService.findByOrderNo("ORD123")).thenReturn(order);

        assertThatThrownBy(() -> payService.pay(9999L, request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("订单不存在");
        verify(alipayStrategy, never()).pay(any());
    }

    @Test
    void shouldRejectExpiredOrder() {
        // sandbox 模式下没有延时取消消息，过期订单若仍可支付会形成"永不失效的待付款"
        order.setExpireTime(LocalDateTime.now().minusSeconds(1));
        when(orderService.findByOrderNo("ORD123")).thenReturn(order);

        assertThatThrownBy(() -> payService.pay(OWNER, request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已过期");
        verify(alipayStrategy, never()).pay(any());
    }

    @Test
    void shouldRejectWhenSameCourseAlreadyPaidByAnotherOrder() {
        // 并发下单可能产生两张待付款单，付款前必须再校验一次，防止同一课程被扣两次钱
        when(orderService.findByOrderNo("ORD123")).thenReturn(order);
        when(orderService.hasPaid(OWNER, 2001L)).thenReturn(true);

        assertThatThrownBy(() -> payService.pay(OWNER, request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已购买");
        verify(alipayStrategy, never()).pay(any());
    }
}
