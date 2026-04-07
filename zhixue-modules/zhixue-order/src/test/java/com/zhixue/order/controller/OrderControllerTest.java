package com.zhixue.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.common.core.exception.GlobalExceptionHandler;
import com.zhixue.order.domain.dto.CreateOrderDTO;
import com.zhixue.order.domain.entity.Order;
import com.zhixue.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderController orderController;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(orderController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldCreateOrderByAliasPath() throws Exception {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo("ORD1001");

        given(orderService.createOrder(any(CreateOrderDTO.class))).willReturn(order);

        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setUserId(1L);
        dto.setProductId(2L);
        dto.setProductType(0);
        dto.setAmount(BigDecimal.valueOf(9.9));

        mockMvc.perform(post("/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderNo").value("ORD1001"));
    }

    @Test
    void shouldGetOrderById() throws Exception {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo("ORD1001");
        given(orderService.findById(1L)).willReturn(order);

        mockMvc.perform(get("/order/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderNo").value("ORD1001"));
    }

    @Test
    void shouldCancelOrderById() throws Exception {
        mockMvc.perform(put("/order/{id}/cancel", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
