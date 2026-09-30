package com.zhixue.marketing.controller;

import com.zhixue.common.core.exception.GlobalExceptionHandler;
import com.zhixue.common.security.config.InternalAccessConstants;
import com.zhixue.common.security.config.SecurityAccessAspect;
import com.zhixue.marketing.domain.dto.SeckillRequestDTO;
import com.zhixue.marketing.domain.dto.SeckillResultVO;
import com.zhixue.marketing.service.SeckillService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 秒杀接口越权防护。
 *
 * <p>修复前实测（学生令牌经网关）：可新建 0.01 元的秒杀活动、可预热库存、
 * 可替用户 1001 签发令牌并以 1001 的身份秒杀。</p>
 */
@ExtendWith(MockitoExtension.class)
class SeckillAccessControlTest {

    private static final long STUDENT = 1003L;

    @Mock
    private SeckillService seckillService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new SeckillController(seckillService));
        factory.setProxyTargetClass(true);
        factory.addAspect(new SecurityAccessAspect());
        mockMvc = MockMvcBuilders.standaloneSetup((Object) factory.getProxy())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder builder, long userId, String... perms) {
        return builder
                .requestAttr(InternalAccessConstants.TRUSTED_REQUEST_ATTR, Boolean.TRUE)
                .header(InternalAccessConstants.USER_ID_HEADER, String.valueOf(userId))
                .header(InternalAccessConstants.USER_PERMISSIONS_HEADER, String.join(",", perms));
    }

    @Test
    void tokenMustBeIssuedToLoginUserIgnoringUserIdParam() throws Exception {
        when(seckillService.issueToken(63001L, STUDENT)).thenReturn("tk");

        mockMvc.perform(asUser(post("/seckill/token"), STUDENT)
                        .param("activityId", "63001")
                        .param("userId", "1001"))
                .andExpect(jsonPath("$.code").value(200));

        verify(seckillService).issueToken(63001L, STUDENT);
        verify(seckillService, never()).issueToken(63001L, 1001L);
    }

    @Test
    void seckillMustRunAsLoginUserIgnoringUserIdInBody() throws Exception {
        SeckillResultVO result = new SeckillResultVO("ORD-SK-1", new BigDecimal("49.00"));
        when(seckillService.seckill(eq(STUDENT), any(SeckillRequestDTO.class))).thenReturn(result);

        mockMvc.perform(asUser(post("/seckill/do"), STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":63001,\"userId\":1001,\"token\":\"tk\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.orderNo").value("ORD-SK-1"));

        verify(seckillService).seckill(eq(STUDENT), any(SeckillRequestDTO.class));
    }

    @Test
    void anonymousCannotSeckill() throws Exception {
        mockMvc.perform(post("/seckill/do")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityId\":63001,\"token\":\"tk\"}"))
                .andExpect(jsonPath("$.code").value(401));

        verify(seckillService, never()).seckill(anyLong(), any());
    }

    @Test
    void studentCannotCreateActivity() throws Exception {
        mockMvc.perform(asUser(post("/seckill"), STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"PWN\",\"productId\":2001,\"seckillPrice\":0.01,\"totalStock\":999,"
                                + "\"startTime\":\"2026-01-01T00:00:00\",\"endTime\":\"2027-01-01T00:00:00\",\"status\":1}"))
                .andExpect(jsonPath("$.code").value(403));

        verify(seckillService, never()).create(any());
    }

    @Test
    void studentCannotPreloadOrChangeStatus() throws Exception {
        mockMvc.perform(asUser(post("/seckill/preload"), STUDENT).param("activityId", "63001"))
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(asUser(put("/seckill/63001/status"), STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":1}"))
                .andExpect(jsonPath("$.code").value(403));

        verify(seckillService, never()).preloadStock(anyLong());
        verify(seckillService, never()).changeStatus(anyLong(), any());
    }

    @Test
    void operatorWithEditPermissionMayPreload() throws Exception {
        mockMvc.perform(asUser(post("/seckill/preload"), 1001L, "marketing:seckill:edit").param("activityId", "63001"))
                .andExpect(jsonPath("$.code").value(200));

        verify(seckillService).preloadStock(63001L);
    }
}
