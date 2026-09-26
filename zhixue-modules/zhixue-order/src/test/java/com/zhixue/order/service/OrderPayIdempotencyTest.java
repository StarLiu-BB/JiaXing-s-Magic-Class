package com.zhixue.order.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixue.order.domain.entity.Order;
import com.zhixue.order.mapper.OrderMapper;
import com.zhixue.order.service.impl.OrderServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付成功的幂等性测试。
 *
 * <p>覆盖阶段 1 验收标准：支付状态变更必须依赖数据库条件更新
 * （WHERE status=0）实现原子幂等，而不是"先查后改"。</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderPayIdempotencyTest {

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        // 纯单元测试不启动 Spring/MyBatis，需手动预热 lambda 字段缓存，
        // 否则 LambdaUpdateWrapper 会抛 "can not find lambda cache"
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), Order.class);
    }

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Order pendingOrder() {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNo("ORD001");
        order.setAmount(new BigDecimal("999.00"));
        order.setStatus(0);
        return order;
    }

    @Test
    void shouldUseConditionalUpdateRatherThanUpdateById() {
        when(orderMapper.update(isNull(), any())).thenReturn(1);

        orderService.paySuccess("ORD001", "alipay", "PAY001");

        // 必须走带条件的 update，禁止使用无条件的 updateById
        verify(orderMapper, times(1)).update(isNull(), any());
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    @Test
    void shouldBeIdempotentWhenConditionalUpdateAffectsNoRow() {
        // 影响 0 行 = 订单已被其它并发请求处理，必须安静返回而非抛错
        when(orderMapper.update(isNull(), any())).thenReturn(0);

        orderService.paySuccess("ORD001", "alipay", "PAY001");

        verify(orderMapper, times(1)).update(isNull(), any());
    }

    @Test
    void concurrentCallbacksShouldResultInSingleSuccessfulUpdate() {
        // 模拟重复回调：第一次成功，后续均因 status 已变更而影响 0 行
        when(orderMapper.update(isNull(), any()))
                .thenReturn(1)
                .thenReturn(0)
                .thenReturn(0);

        orderService.paySuccess("ORD001", "alipay", "PAY001");
        orderService.paySuccess("ORD001", "alipay", "PAY001");
        orderService.paySuccess("ORD001", "alipay", "PAY001");

        verify(orderMapper, times(3)).update(isNull(), any());
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    @Test
    void cancelShouldAlsoUseConditionalUpdate() {
        when(orderMapper.selectOne(any())).thenReturn(pendingOrder());
        when(orderMapper.update(isNull(), any())).thenReturn(1);

        orderService.cancelOrder("ORD001", "用户取消");

        verify(orderMapper, times(1)).update(isNull(), any());
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void conditionalUpdateShouldCarryStatusPredicate() {
        when(orderMapper.update(isNull(), any())).thenReturn(1);

        orderService.paySuccess("ORD001", "alipay", "PAY001");

        ArgumentCaptor<Wrapper<Order>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(orderMapper).update(isNull(), captor.capture());
        String sql = captor.getValue().getTargetSql();
        // 条件里必须同时包含订单号与原始状态，才能保证并发下只有一个请求生效
        assertThat(sql).contains("order_no");
        assertThat(sql).contains("status");
    }
}
