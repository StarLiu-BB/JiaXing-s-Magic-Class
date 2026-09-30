package com.zhixue.order.controller;

import com.zhixue.common.core.domain.PageQuery;
import com.zhixue.common.core.exception.GlobalExceptionHandler;
import com.zhixue.common.security.config.InternalAccessConstants;
import com.zhixue.common.security.config.SecurityAccessAspect;
import com.zhixue.order.domain.dto.CreateOrderDTO;
import com.zhixue.order.domain.entity.Order;
import com.zhixue.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 订单接口的越权防护测试。
 *
 * <p>修复前实测：订单服务 permitAll 且 userId 由客户端传入，学生可查询全站订单、
 * 替他人下单、给他人订单退款。本测试经过真实的 {@link SecurityAccessAspect}，
 * 身份只来自网关注入的可信请求头。</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderAccessControlTest {

    private static final long STUDENT = 1003L;
    private static final long OTHER_USER = 1001L;

    @Mock
    private OrderService orderService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new OrderController(orderService));
        factory.setProxyTargetClass(true);
        factory.addAspect(new SecurityAccessAspect());
        mockMvc = MockMvcBuilders.standaloneSetup((Object) factory.getProxy())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    /** 模拟经网关鉴权后的请求：InternalAccessFilter 已校验内部令牌并打上可信标记。 */
    private MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder builder, long userId, String... perms) {
        return builder
                .requestAttr(InternalAccessConstants.TRUSTED_REQUEST_ATTR, Boolean.TRUE)
                .header(InternalAccessConstants.USER_ID_HEADER, String.valueOf(userId))
                .header(InternalAccessConstants.USER_PERMISSIONS_HEADER, String.join(",", perms));
    }

    private Order orderOf(long userId) {
        Order order = new Order();
        order.setId(50001L);
        order.setOrderNo("ORD50001");
        order.setUserId(userId);
        order.setStatus(0);
        return order;
    }

    @Test
    void createShouldUseLoginUserAndIgnoreUserIdInBody() throws Exception {
        when(orderService.createOrder(anyLong(), any(CreateOrderDTO.class))).thenReturn(orderOf(STUDENT));

        mockMvc.perform(asUser(post("/order/create"), STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + OTHER_USER + ",\"productId\":2001,\"productType\":1,\"amount\":0.01}"))
                .andExpect(jsonPath("$.code").value(200));

        verify(orderService).createOrder(eq(STUDENT), any(CreateOrderDTO.class));
    }

    @Test
    void aliasCreatePathShouldApplySameIdentityRule() throws Exception {
        when(orderService.createOrder(anyLong(), any(CreateOrderDTO.class))).thenReturn(orderOf(STUDENT));

        mockMvc.perform(asUser(post("/order"), STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":" + OTHER_USER + ",\"productId\":2001}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderNo").value("ORD50001"));

        verify(orderService).createOrder(eq(STUDENT), any(CreateOrderDTO.class));
    }

    @Test
    void createShouldRejectAnonymousRequest() throws Exception {
        mockMvc.perform(post("/order/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":2001}"))
                .andExpect(jsonPath("$.code").value(401));

        verify(orderService, never()).createOrder(anyLong(), any());
    }

    @Test
    void forgedUserHeaderWithoutTrustedMarkShouldBeTreatedAsAnonymous() throws Exception {
        // 未经 InternalAccessFilter 校验的 X-User-Id 不可信，不能冒充任何人
        mockMvc.perform(post("/order/create")
                        .header(InternalAccessConstants.USER_ID_HEADER, String.valueOf(OTHER_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":2001}"))
                .andExpect(jsonPath("$.code").value(401));

        verify(orderService, never()).createOrder(anyLong(), any());
    }

    @Test
    void listForStudentShouldBeForcedToOwnOrders() throws Exception {
        mockMvc.perform(asUser(get("/order/list"), STUDENT).param("userId", String.valueOf(OTHER_USER)))
                .andExpect(jsonPath("$.code").value(200));

        verify(orderService).pageOrders(any(PageQuery.class), isNull(), eq(STUDENT), isNull());
    }

    @Test
    void listWithoutUserIdForStudentShouldNotReturnWholeSite() throws Exception {
        mockMvc.perform(asUser(get("/order/list"), STUDENT))
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<Long> userIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(orderService).pageOrders(any(PageQuery.class), isNull(), userIdCaptor.capture(), isNull());
        assertThat(userIdCaptor.getValue()).isEqualTo(STUDENT);
    }

    @Test
    void listForManagerMayQueryAnyUser() throws Exception {
        mockMvc.perform(asUser(get("/order/list"), OTHER_USER, "order:list").param("userId", String.valueOf(STUDENT)))
                .andExpect(jsonPath("$.code").value(200));

        verify(orderService).pageOrders(any(PageQuery.class), isNull(), eq(STUDENT), isNull());
    }

    @Test
    void readingOthersOrderShouldLookLikeNotFound() throws Exception {
        // 对非本人订单统一返回"不存在"，避免通过 ID/订单号枚举他人订单
        when(orderService.findById(50001L)).thenReturn(orderOf(OTHER_USER));

        mockMvc.perform(asUser(get("/order/{id}", 50001L), STUDENT))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void readingOthersOrderByOrderNoShouldLookLikeNotFound() throws Exception {
        when(orderService.findByOrderNo("ORD50001")).thenReturn(orderOf(OTHER_USER));

        mockMvc.perform(asUser(get("/order/detail"), STUDENT).param("orderNo", "ORD50001"))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void readingOwnOrderShouldSucceed() throws Exception {
        when(orderService.findByOrderNo("ORD50001")).thenReturn(orderOf(STUDENT));

        mockMvc.perform(asUser(get("/order/detail"), STUDENT).param("orderNo", "ORD50001"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderNo").value("ORD50001"));
    }

    @Test
    void cancellingOthersOrderShouldBeRejected() throws Exception {
        when(orderService.findById(50001L)).thenReturn(orderOf(OTHER_USER));

        mockMvc.perform(asUser(put("/order/{id}/cancel", 50001L), STUDENT))
                .andExpect(jsonPath("$.code").value(404));

        verify(orderService, never()).cancelOrderById(anyLong(), any());
    }

    @Test
    void cancellingOwnOrderShouldSucceed() throws Exception {
        when(orderService.findById(50001L)).thenReturn(orderOf(STUDENT));

        mockMvc.perform(asUser(put("/order/{id}/cancel", 50001L), STUDENT))
                .andExpect(jsonPath("$.code").value(200));

        verify(orderService).cancelOrderById(eq(50001L), any());
    }

    @Test
    void studentMustNotRefundAnyOrder() throws Exception {
        mockMvc.perform(asUser(put("/order/{id}/refund", 50001L), STUDENT))
                .andExpect(jsonPath("$.code").value(403));

        verify(orderService, never()).refundOrder(anyLong(), any());
    }

    @Test
    void managerWithRefundPermissionMayRefund() throws Exception {
        mockMvc.perform(asUser(put("/order/{id}/refund", 50001L), OTHER_USER, "order:refund"))
                .andExpect(jsonPath("$.code").value(200));

        verify(orderService).refundOrder(eq(50001L), any());
    }

    @Test
    void studentMustNotSeeSiteWideStatistics() throws Exception {
        mockMvc.perform(asUser(get("/order/statistics"), STUDENT))
                .andExpect(jsonPath("$.code").value(403));

        verify(orderService, never()).statistics(any(), any());
    }
}
