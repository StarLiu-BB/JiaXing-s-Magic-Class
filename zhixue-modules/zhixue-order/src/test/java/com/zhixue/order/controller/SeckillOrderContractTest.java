package com.zhixue.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.api.order.RemoteOrderService;
import com.zhixue.api.order.domain.SeckillOrderRequest;
import com.zhixue.common.core.exception.GlobalExceptionHandler;
import com.zhixue.common.security.config.InternalAccessConstants;
import com.zhixue.order.domain.entity.Order;
import com.zhixue.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 秒杀成交后在订单服务生成真实订单：服务间契约 + 访问控制。
 *
 * <p>秒杀价由营销服务决定，因此该接口必须仅限可信内部调用，
 * 否则任何人都能以任意价格给自己下单。</p>
 */
@ExtendWith(MockitoExtension.class)
class SeckillOrderContractTest {

    @Mock
    private OrderService orderService;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(orderService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String feignPath() throws Exception {
        return RemoteOrderService.class
                .getMethod("createSeckillOrder", SeckillOrderRequest.class, String.class, String.class)
                .getAnnotation(PostMapping.class).value()[0];
    }

    private String body() throws Exception {
        SeckillOrderRequest request = new SeckillOrderRequest();
        request.setUserId(1003L);
        request.setProductId(2004L);
        request.setAmount(new BigDecimal("49.00"));
        request.setActivityId(63001L);
        return objectMapper.writeValueAsString(request);
    }

    @Test
    void trustedInternalCallShouldCreateOrderAtGivenPrice() throws Exception {
        Order order = new Order();
        order.setOrderNo("ORD-SK-1");
        order.setAmount(new BigDecimal("49.00"));
        order.setStatus(0);
        when(orderService.createSeckillOrder(eq(1003L), eq(2004L), any(BigDecimal.class), eq(63001L))).thenReturn(order);

        mockMvc.perform(post(feignPath())
                        .requestAttr(InternalAccessConstants.TRUSTED_REQUEST_ATTR, Boolean.TRUE)
                        .header(InternalAccessConstants.INNER_CALL_HEADER, "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderNo").value("ORD-SK-1"));
    }

    @Test
    void trustedQueryUsesFeignPathAndExternalQueryIsDenied() throws Exception {
        String path = RemoteOrderService.class.getMethod("findSeckillOrder", Long.class, Long.class, String.class, String.class)
                .getAnnotation(org.springframework.web.bind.annotation.GetMapping.class).value()[0];
        Order order = new Order(); order.setOrderNo("ORD-SK-1");
        when(orderService.findSeckillOrder(63001L, 1003L)).thenReturn(order);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
                .param("activityId", "63001").param("userId", "1003")
                .requestAttr(InternalAccessConstants.TRUSTED_REQUEST_ATTR, true)
                .header(InternalAccessConstants.INNER_CALL_HEADER, "true"))
                .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.orderNo").value("ORD-SK-1"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
                .param("activityId", "63001").param("userId", "1003")
                .requestAttr(InternalAccessConstants.TRUSTED_REQUEST_ATTR, true)
                .header(InternalAccessConstants.USER_ID_HEADER, "1003"))
                .andExpect(jsonPath("$.code").value(403));
        verify(orderService).findSeckillOrder(63001L, 1003L);
    }

    @Test
    void userRequestThroughGatewayMustNotCreateOrderAtArbitraryPrice() throws Exception {
        mockMvc.perform(post(feignPath())
                        .requestAttr(InternalAccessConstants.TRUSTED_REQUEST_ATTR, Boolean.TRUE)
                        .header(InternalAccessConstants.USER_ID_HEADER, "1003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(jsonPath("$.code").value(403));

        verify(orderService, never()).createSeckillOrder(anyLong(), anyLong(), any(), anyLong());
    }
}
