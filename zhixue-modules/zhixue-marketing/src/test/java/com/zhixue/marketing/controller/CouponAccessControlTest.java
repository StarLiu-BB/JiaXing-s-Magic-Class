package com.zhixue.marketing.controller;

import com.zhixue.common.core.exception.GlobalExceptionHandler;
import com.zhixue.common.security.config.InternalAccessConstants;
import com.zhixue.common.security.config.SecurityAccessAspect;
import com.zhixue.marketing.domain.dto.CouponApplyDTO;
import com.zhixue.marketing.domain.dto.CouponClaimDTO;
import com.zhixue.marketing.service.CouponService;
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

import java.util.List;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 优惠券接口越权防护。
 *
 * <p>修复前实测（学生令牌经网关）：可新建面额 999、门槛 0 的优惠券；可替用户 1002 领券；
 * 可查询任意用户的券。</p>
 */
@ExtendWith(MockitoExtension.class)
class CouponAccessControlTest {

    private static final long STUDENT = 1003L;

    @Mock
    private CouponService couponService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new CouponController(couponService));
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
    void studentCannotCreateCoupon() throws Exception {
        mockMvc.perform(asUser(post("/coupon"), STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"PWN\",\"totalCount\":999,\"discount\":999,\"thresholdAmount\":0,"
                                + "\"startTime\":\"2026-01-01T00:00:00\",\"endTime\":\"2099-01-01T00:00:00\",\"userLimit\":99}"))
                .andExpect(jsonPath("$.code").value(403));

        verify(couponService, never()).create(any());
    }

    @Test
    void studentCannotIssueOrListAllCoupons() throws Exception {
        mockMvc.perform(asUser(post("/coupon/61001/issue"), STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[1003]}"))
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(asUser(get("/coupon/list"), STUDENT))
                .andExpect(jsonPath("$.code").value(403));

        verify(couponService, never()).issue(anyLong(), any());
    }

    @Test
    void claimMustBeForLoginUserIgnoringUserIdInBody() throws Exception {
        mockMvc.perform(asUser(post("/coupon/claim"), STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"couponId\":61001,\"userId\":1002}"))
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<CouponClaimDTO> captor = ArgumentCaptor.forClass(CouponClaimDTO.class);
        verify(couponService).claim(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(STUDENT);
    }

    @Test
    void claimWithoutUserIdInBodyShouldStillWork() throws Exception {
        // 客户端不再需要（也不应该）传 userId
        mockMvc.perform(asUser(post("/coupon/claim"), STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"couponId\":61001}"))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void userCouponsForStudentAreForcedToOwn() throws Exception {
        when(couponService.listUserCoupons(STUDENT, null)).thenReturn(List.of());

        mockMvc.perform(asUser(get("/coupon/user/list"), STUDENT).param("userId", "1001"))
                .andExpect(jsonPath("$.code").value(200));

        verify(couponService).listUserCoupons(eq(STUDENT), isNull());
        verify(couponService, never()).listUserCoupons(eq(1001L), any());
    }

    @Test
    void applyMustUseLoginUser() throws Exception {
        mockMvc.perform(asUser(post("/coupon/apply"), STUDENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1001,\"couponUserId\":62001,\"orderAmount\":199}"))
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<CouponApplyDTO> captor = ArgumentCaptor.forClass(CouponApplyDTO.class);
        verify(couponService).applyCoupon(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(STUDENT);
    }

    @Test
    void operatorWithEditPermissionMayIssue() throws Exception {
        mockMvc.perform(asUser(post("/coupon/61001/issue"), 1001L, "marketing:coupon:edit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userIds\":[1003]}"))
                .andExpect(jsonPath("$.code").value(200));

        verify(couponService).issue(eq(61001L), eq(List.of(1003L)));
    }
}
