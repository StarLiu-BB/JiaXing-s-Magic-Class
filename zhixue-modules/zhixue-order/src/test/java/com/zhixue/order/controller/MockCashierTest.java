package com.zhixue.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.exception.GlobalExceptionHandler;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.security.config.InternalAccessConstants;
import com.zhixue.order.domain.dto.PayRequestDTO;
import com.zhixue.order.domain.dto.PayResponse;
import com.zhixue.order.domain.entity.Order;
import com.zhixue.order.security.PayCallbackVerifier;
import com.zhixue.order.service.OrderService;
import com.zhixue.order.service.PayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 模拟收银台测试。
 *
 * <p>模拟收银台代替尚未接入的真实渠道"通知我们付款成功"。它必须与真实回调走同一条
 * 验签 + 金额核对 + 幂等入账路径，这样将来替换为真实渠道时，这条路径已被证明可用；
 * 同时它本质上是"免费付款按钮"，必须经过与发起支付完全相同的前置校验。</p>
 */
@ExtendWith(MockitoExtension.class)
class MockCashierTest {

    private static final long OWNER = 1003L;

    @Mock
    private PayService payService;
    @Mock
    private OrderService orderService;
    @Mock
    private RabbitTemplate rabbitTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PayCallbackVerifier verifier = new PayCallbackVerifier();
        ReflectionTestUtils.setField(verifier, "callbackSecret", "unit-test-callback-secret-0123456789abcdef");
        PayController controller = new PayController(payService, orderService, rabbitTemplate, new ObjectMapper(), verifier);
        ReflectionTestUtils.setField(controller, "mqMode", "sandbox");
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private MockHttpServletRequestBuilder confirmAs(long userId, String orderNo) {
        return post("/pay/mock/confirm")
                .requestAttr(InternalAccessConstants.TRUSTED_REQUEST_ATTR, Boolean.TRUE)
                .header(InternalAccessConstants.USER_ID_HEADER, String.valueOf(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderNo\":\"" + orderNo + "\"}");
    }

    private Order pendingOrder() {
        Order order = new Order();
        order.setOrderNo("ORD-MOCK-1");
        order.setUserId(OWNER);
        order.setProductId(2001L);
        order.setAmount(new BigDecimal("199.00"));
        order.setStatus(0);
        order.setExpireTime(LocalDateTime.now().plusMinutes(10));
        return order;
    }

    @Test
    void confirmShouldReuseAllPayPreconditionsWithMockChannel() throws Exception {
        when(payService.pay(eq(OWNER), any(PayRequestDTO.class))).thenReturn(new PayResponse(null, "mock", "mock", "ORD-MOCK-1", null));
        when(orderService.findByOrderNo("ORD-MOCK-1")).thenReturn(pendingOrder());

        mockMvc.perform(confirmAs(OWNER, "ORD-MOCK-1")).andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<PayRequestDTO> captor = ArgumentCaptor.forClass(PayRequestDTO.class);
        verify(payService).pay(eq(OWNER), captor.capture());
        assertThat(captor.getValue().getOrderNo()).isEqualTo("ORD-MOCK-1");
        assertThat(captor.getValue().getPayChannel()).isEqualTo("mock");
    }

    @Test
    void confirmShouldSettleThroughVerifiedCallbackPath() throws Exception {
        when(payService.pay(eq(OWNER), any(PayRequestDTO.class))).thenReturn(new PayResponse(null, "mock", "mock", "ORD-MOCK-1", null));
        when(orderService.findByOrderNo("ORD-MOCK-1")).thenReturn(pendingOrder());

        mockMvc.perform(confirmAs(OWNER, "ORD-MOCK-1")).andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<String> payNo = ArgumentCaptor.forClass(String.class);
        verify(orderService).paySuccess(eq("ORD-MOCK-1"), eq("mock"), payNo.capture());
        // 模拟渠道也必须生成独立流水号，便于与真实渠道流水区分和对账
        assertThat(payNo.getValue()).startsWith("MOCK");
    }

    @Test
    void confirmMustNotSettleWhenPreconditionsFail() throws Exception {
        // 他人订单 / 已过期 / 已购买 / 模拟收银台未启用，均由 payService.pay 拒绝
        when(payService.pay(eq(OWNER), any(PayRequestDTO.class)))
                .thenThrow(new ServiceException(HttpStatus.NOT_FOUND, "订单不存在"));

        mockMvc.perform(confirmAs(OWNER, "ORD-OTHERS")).andExpect(jsonPath("$.code").value(404));

        verify(orderService, never()).paySuccess(anyString(), anyString(), anyString());
    }

    @Test
    void confirmRequiresLogin() throws Exception {
        mockMvc.perform(post("/pay/mock/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"ORD-MOCK-1\"}"))
                .andExpect(jsonPath("$.code").value(401));

        verify(payService, never()).pay(any(), any());
        verify(orderService, never()).paySuccess(anyString(), anyString(), anyString());
    }
}
