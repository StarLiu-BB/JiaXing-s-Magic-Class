package com.zhixue.order.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixue.api.course.RemoteCourseService;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.order.domain.entity.Order;
import com.zhixue.order.mapper.OrderMapper;
import com.zhixue.order.service.impl.OrderServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SeckillIdempotencyTest {
    @BeforeAll static void init() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Order.class);
    }
    private final OrderMapper mapper = mock(OrderMapper.class);
    private final RabbitTemplate mq = mock(RabbitTemplate.class);
    private final OrderServiceImpl service = new OrderServiceImpl(mapper, mq, mock(RemoteCourseService.class));
    private Order order(int status) {
        Order order = new Order(); order.setId(1L); order.setOrderNo("SK-EXIST");
        order.setUserId(1003L); order.setProductId(2007L); order.setActivityId(63001L);
        order.setAmount(new BigDecimal("9.90")); order.setStatus(status); order.setDeleted(0);
        order.setExpireTime(LocalDateTime.now().plusMinutes(20)); return order;
    }
    private Order create() { return service.createSeckillOrder(1003L, 2007L, new BigDecimal("9.90"), 63001L); }

    @Test void sameActivityAndUserReturnsExistingOrderWithoutInserting() {
        when(mapper.findSeckillOrder(63001L, 1003L)).thenReturn(order(0));
        assertThat(create().getOrderNo()).isEqualTo("SK-EXIST");
        verify(mapper, never()).insert(any(Order.class));
    }
    @Test void uniqueConflictUsesCurrentReadNotOldTransactionSnapshot() {
        when(mapper.insert(any(Order.class))).thenThrow(new DuplicateKeyException("unique"));
        when(mapper.lockSeckillOrder(63001L, 1003L)).thenReturn(order(0));
        assertThat(create().getOrderNo()).isEqualTo("SK-EXIST");
        verify(mapper).lockSeckillOrder(63001L, 1003L);
    }
    @Test void unrelatedUniqueConflictIsNotReportedAsSuccess() {
        when(mapper.insert(any(Order.class))).thenThrow(new DuplicateKeyException("order_no"));
        assertThatThrownBy(this::create).isInstanceOf(DuplicateKeyException.class);
    }
    @Test void newOrderPersistsActivityId() {
        when(mapper.insert(any(Order.class))).thenReturn(1);
        assertThat(create().getActivityId()).isEqualTo(63001L);
    }
    @Test void changedProductOrPriceForSameKeyIsRejected() {
        Order existing = order(0); existing.setProductId(2005L);
        when(mapper.findSeckillOrder(63001L, 1003L)).thenReturn(existing);
        assertThatThrownBy(this::create).isInstanceOf(ServiceException.class).hasMessageContaining("不一致");
        existing.setProductId(2007L); existing.setAmount(new BigDecimal("1.00"));
        assertThatThrownBy(this::create).hasMessageContaining("不一致");
    }
    @Test void canceledRefundedDeletedOrExpiredOrdersDoNotReopenEligibility() {
        for (int status : new int[]{2, 3}) {
            when(mapper.findSeckillOrder(63001L, 1003L)).thenReturn(order(status));
            assertThatThrownBy(this::create).isInstanceOf(ServiceException.class);
        }
        Order existing = order(0); existing.setDeleted(1);
        when(mapper.findSeckillOrder(63001L, 1003L)).thenReturn(existing);
        assertThatThrownBy(this::create).isInstanceOf(ServiceException.class);
        existing.setDeleted(0); existing.setExpireTime(LocalDateTime.now().minusSeconds(1));
        assertThatThrownBy(this::create).hasMessageContaining("过期");
        verify(mapper, never()).insert(any(Order.class));
    }
    @Test void paidSameFingerprintCanBeConfirmedEvenAfterExpiry() {
        Order existing = order(1); existing.setExpireTime(LocalDateTime.now().minusHours(1));
        when(mapper.findSeckillOrder(63001L, 1003L)).thenReturn(existing);
        assertThat(create().getStatus()).isEqualTo(1);
    }
    @Test void missingActivityIdIsRejectedBeforeDatabaseAccess() {
        assertThatThrownBy(() -> service.createSeckillOrder(1003L, 2007L, new BigDecimal("9.90"), null))
                .isInstanceOf(ServiceException.class);
        verifyNoInteractions(mapper);
    }
}
