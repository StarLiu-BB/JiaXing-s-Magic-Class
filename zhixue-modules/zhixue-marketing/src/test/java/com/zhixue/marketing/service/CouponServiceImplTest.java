package com.zhixue.marketing.service;

import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.marketing.domain.dto.CouponClaimDTO;
import com.zhixue.marketing.domain.entity.Coupon;
import com.zhixue.marketing.mapper.CouponMapper;
import com.zhixue.marketing.mapper.CouponUserMapper;
import com.zhixue.marketing.service.impl.CouponServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponServiceImplTest {

    @Mock
    private CouponMapper couponMapper;

    @Mock
    private CouponUserMapper couponUserMapper;

    @InjectMocks
    private CouponServiceImpl couponService;

    private Coupon coupon;

    @BeforeEach
    void setUp() {
        coupon = new Coupon();
        coupon.setId(1L);
        coupon.setName("测试券");
        coupon.setTotalCount(100);
        coupon.setRemainCount(0);
        coupon.setDiscount(BigDecimal.TEN);
        coupon.setThresholdAmount(BigDecimal.ONE);
        coupon.setStartTime(LocalDateTime.now().minusDays(1));
        coupon.setEndTime(LocalDateTime.now().plusDays(1));
        coupon.setStatus(1);
        coupon.setUserLimit(1);
    }

    @Test
    void shouldRejectClaimWhenNoRemainCount() {
        when(couponMapper.selectById(1L)).thenReturn(coupon);

        CouponClaimDTO dto = new CouponClaimDTO();
        dto.setCouponId(1L);
        dto.setUserId(1001L);

        assertThatThrownBy(() -> couponService.claim(dto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已领完");
    }

    @Test
    void shouldRejectClaimWhenReachUserLimit() {
        coupon.setRemainCount(10);
        when(couponMapper.selectById(1L)).thenReturn(coupon);
        when(couponUserMapper.selectCount(any())).thenReturn(1L);

        CouponClaimDTO dto = new CouponClaimDTO();
        dto.setCouponId(1L);
        dto.setUserId(1001L);

        assertThatThrownBy(() -> couponService.claim(dto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已达到领取上限");
    }
}
