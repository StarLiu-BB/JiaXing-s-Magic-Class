package com.zhixue.marketing.controller;

import com.zhixue.common.core.exception.GlobalExceptionHandler;
import com.zhixue.marketing.domain.dto.CouponCreateDTO;
import com.zhixue.marketing.domain.entity.Coupon;
import com.zhixue.marketing.service.CouponService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CouponControllerTest {

    @Mock
    private CouponService couponService;

    @InjectMocks
    private CouponController couponController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(couponController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldCreateCoupon() throws Exception {
        Coupon coupon = new Coupon();
        coupon.setId(1L);
        coupon.setName("新用户券");
        given(couponService.create(any(CouponCreateDTO.class))).willReturn(coupon);
        String payload = "{"
                + "\"name\":\"新用户券\","
                + "\"totalCount\":100,"
                + "\"discount\":10,"
                + "\"thresholdAmount\":100,"
                + "\"startTime\":\"" + LocalDateTime.now().plusMinutes(1) + "\","
                + "\"endTime\":\"" + LocalDateTime.now().plusDays(7) + "\","
                + "\"userLimit\":1"
                + "}";

        mockMvc.perform(post("/coupon")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("新用户券"));
    }

    @Test
    void shouldGetCouponById() throws Exception {
        Coupon coupon = new Coupon();
        coupon.setId(1L);
        coupon.setName("新用户券");
        given(couponService.getById(1L)).willReturn(coupon);

        mockMvc.perform(get("/coupon/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1L));
    }
}
