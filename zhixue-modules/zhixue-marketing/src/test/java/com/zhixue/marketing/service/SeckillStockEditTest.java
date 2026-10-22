package com.zhixue.marketing.service;

import com.zhixue.api.order.RemoteOrderService;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.security.config.SecurityProperties;
import com.zhixue.marketing.domain.dto.SeckillActivityDTO;
import com.zhixue.marketing.domain.entity.SeckillActivity;
import com.zhixue.marketing.mapper.SeckillActivityMapper;
import com.zhixue.marketing.mapper.SeckillOrderMapper;
import com.zhixue.marketing.service.impl.SeckillServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SeckillStockEditTest {
    @Test void statusChangeUsesLockedCurrentRowAndCannotOverwriteOldTerms() {
        var mapper = mock(SeckillActivityMapper.class);
        var service = new SeckillServiceImpl(mapper, mock(SeckillOrderMapper.class), mock(StringRedisTemplate.class),
                mock(RemoteOrderService.class), new SecurityProperties());
        SeckillActivity current = new SeckillActivity(); current.setId(1L); current.setTotalStock(10);
        when(mapper.lockActivity(1L)).thenReturn(current);
        service.changeStatus(1L, 1);
        var captured = org.mockito.ArgumentCaptor.forClass(SeckillActivity.class);
        verify(mapper).updateById(captured.capture());
        assertThat(captured.getValue().getTotalStock()).isEqualTo(10);
        verify(mapper, never()).selectById(1L);
    }

    @Test void editLocksActivityBeforeInspectingRedisStock() {
        var mapper = mock(SeckillActivityMapper.class);
        var redis = mock(StringRedisTemplate.class);
        var service = new SeckillServiceImpl(mapper, mock(SeckillOrderMapper.class), redis,
                mock(RemoteOrderService.class), new SecurityProperties());
        ReflectionTestUtils.setField(service, "stockKeyPrefix", "seckill:stock:");
        SeckillActivity activity = new SeckillActivity(); activity.setId(1L); activity.setProductId(2007L);
        activity.setTotalStock(10); activity.setSeckillPrice(new BigDecimal("9.90"));
        activity.setStartTime(LocalDateTime.now().plusHours(1));
        when(mapper.lockActivity(1L)).thenReturn(activity);
        SeckillActivityDTO dto = new SeckillActivityDTO(); dto.setId(1L); dto.setProductId(2007L);
        dto.setTotalStock(20); dto.setSeckillPrice(new BigDecimal("9.90"));
        dto.setStartTime(activity.getStartTime()); dto.setEndTime(LocalDateTime.now().plusHours(2));
        service.update(dto);
        var order = inOrder(mapper, redis);
        order.verify(mapper).lockActivity(1L);
        order.verify(redis).hasKey("seckill:stock:1");
    }

    @Test void loadedReservationCannotChangeStockProductOrPrice() {
        var activities = mock(SeckillActivityMapper.class);
        var redis = mock(StringRedisTemplate.class);
        var service = new SeckillServiceImpl(activities, mock(SeckillOrderMapper.class), redis,
                mock(RemoteOrderService.class), new SecurityProperties());
        ReflectionTestUtils.setField(service, "stockKeyPrefix", "seckill:stock:");
        SeckillActivity activity = new SeckillActivity(); activity.setId(1L); activity.setProductId(2007L);
        activity.setTotalStock(10); activity.setSeckillPrice(new BigDecimal("9.90"));
        when(activities.lockActivity(1L)).thenReturn(activity);
        when(redis.hasKey("seckill:stock:1")).thenReturn(true);
        SeckillActivityDTO dto = new SeckillActivityDTO(); dto.setId(1L); dto.setProductId(2007L);
        dto.setTotalStock(20); dto.setSeckillPrice(new BigDecimal("9.90"));
        dto.setStartTime(LocalDateTime.now().minusHours(1)); dto.setEndTime(LocalDateTime.now().plusHours(1));
        assertThatThrownBy(() -> service.update(dto)).isInstanceOf(ServiceException.class);
        dto.setTotalStock(10); dto.setProductId(2005L);
        assertThatThrownBy(() -> service.update(dto)).isInstanceOf(ServiceException.class);
        dto.setProductId(2007L); dto.setSeckillPrice(new BigDecimal("1.00"));
        assertThatThrownBy(() -> service.update(dto)).isInstanceOf(ServiceException.class);
        verify(activities, never()).updateById(any(SeckillActivity.class));
    }
}
