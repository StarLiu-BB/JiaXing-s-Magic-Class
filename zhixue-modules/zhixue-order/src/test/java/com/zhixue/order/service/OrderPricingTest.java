package com.zhixue.order.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixue.api.course.RemoteCourseService;
import com.zhixue.api.course.domain.CourseInfo;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.order.domain.dto.CreateOrderDTO;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 下单核价与防重复购买测试。
 *
 * <p>修复前：订单金额直接取客户端传入的 amount，传 0.01 即可按 0.01 元下单；
 * 同一课程可无限重复下单、重复付款。</p>
 */
@ExtendWith(MockitoExtension.class)
class OrderPricingTest {

    private static final long USER = 1003L;
    private static final long COURSE = 2001L;

    @BeforeAll
    static void initMybatisPlusLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Order.class);
    }

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private RemoteCourseService remoteCourseService;

    @InjectMocks
    private OrderServiceImpl orderService;

    private CreateOrderDTO request() {
        CreateOrderDTO dto = new CreateOrderDTO();
        dto.setProductId(COURSE);
        return dto;
    }

    private CourseInfo course(String price, int status, int shelfStatus) {
        CourseInfo info = new CourseInfo();
        info.setId(COURSE);
        info.setTitle("Spring Cloud 微服务实战");
        info.setPrice(new BigDecimal(price));
        info.setStatus(status);
        info.setShelfStatus(shelfStatus);
        return info;
    }

    private Order existing(int status, LocalDateTime expireTime) {
        Order order = new Order();
        order.setOrderNo("ORD-EXISTING");
        order.setUserId(USER);
        order.setProductId(COURSE);
        order.setStatus(status);
        order.setAmount(new BigDecimal("199.00"));
        order.setExpireTime(expireTime);
        return order;
    }

    @Test
    void amountMustComeFromCourseServiceNotFromClient() {
        when(remoteCourseService.getCourse(COURSE)).thenReturn(R.ok(course("199.00", 1, 1)));
        when(orderMapper.selectList(any())).thenReturn(List.of());
        when(orderMapper.insert(any(Order.class))).thenReturn(1);

        orderService.createOrder(USER, request());

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insert(captor.capture());
        Order saved = captor.getValue();
        assertThat(saved.getAmount()).isEqualByComparingTo("199.00");
        assertThat(saved.getUserId()).isEqualTo(USER);
        // schema 约定 0=课程；客户端曾错误地传 1，服务端不再采信客户端的商品类型
        assertThat(saved.getProductType()).isZero();
    }

    @Test
    void shouldFailClosedWhenCourseServiceUnavailable() {
        // 取不到真实价格时绝不能退回信任客户端金额
        when(remoteCourseService.getCourse(COURSE)).thenReturn(R.fail(503, "课程服务不可用"));

        assertThatThrownBy(() -> orderService.createOrder(USER, request()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("暂不可购买");
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    void shouldFailClosedWhenCourseServiceThrows() {
        when(remoteCourseService.getCourse(COURSE)).thenThrow(new RuntimeException("connect timed out"));

        assertThatThrownBy(() -> orderService.createOrder(USER, request()))
                .isInstanceOf(ServiceException.class);
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    void shouldRejectUnpublishedCourse() {
        when(remoteCourseService.getCourse(COURSE)).thenReturn(R.ok(course("199.00", 0, 1)));

        assertThatThrownBy(() -> orderService.createOrder(USER, request()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("暂不可购买");
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    void shouldRejectOffShelfCourse() {
        when(remoteCourseService.getCourse(COURSE)).thenReturn(R.ok(course("199.00", 1, 0)));

        assertThatThrownBy(() -> orderService.createOrder(USER, request()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("暂不可购买");
    }

    @Test
    void freeCourseNeedsNoOrder() {
        when(remoteCourseService.getCourse(COURSE)).thenReturn(R.ok(course("0.00", 1, 1)));

        assertThatThrownBy(() -> orderService.createOrder(USER, request()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("免费");
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    void shouldRejectWhenAlreadyPurchased() {
        when(remoteCourseService.getCourse(COURSE)).thenReturn(R.ok(course("199.00", 1, 1)));
        when(orderMapper.selectList(any())).thenReturn(List.of(existing(1, LocalDateTime.now().minusDays(1))));

        assertThatThrownBy(() -> orderService.createOrder(USER, request()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已购买");
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    void shouldReuseUnexpiredPendingOrderInsteadOfCreatingDuplicate() {
        when(remoteCourseService.getCourse(COURSE)).thenReturn(R.ok(course("199.00", 1, 1)));
        when(orderMapper.selectList(any())).thenReturn(List.of(existing(0, LocalDateTime.now().plusMinutes(10))));

        Order result = orderService.createOrder(USER, request());

        assertThat(result.getOrderNo()).isEqualTo("ORD-EXISTING");
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    void expiredPendingOrderShouldNotBlockNewOrder() {
        when(remoteCourseService.getCourse(COURSE)).thenReturn(R.ok(course("199.00", 1, 1)));
        when(orderMapper.selectList(any())).thenReturn(List.of(existing(0, LocalDateTime.now().minusMinutes(1))));
        when(orderMapper.insert(any(Order.class))).thenReturn(1);

        Order result = orderService.createOrder(USER, request());

        assertThat(result.getOrderNo()).isNotEqualTo("ORD-EXISTING");
        verify(orderMapper).insert(any(Order.class));
    }

    @Test
    void seckillOrderShouldUseGivenPriceAndNotCallCourseService() {
        // 秒杀价由营销服务（可信内部调用）决定，不能再按课程原价核价
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(orderMapper.insert(any(Order.class))).thenReturn(1);

        Order order = orderService.createSeckillOrder(USER, COURSE, new BigDecimal("49.00"), 63001L);

        assertThat(order.getAmount()).isEqualByComparingTo("49.00");
        assertThat(order.getUserId()).isEqualTo(USER);
        assertThat(order.getProductType()).isZero();
        assertThat(order.getStatus()).isZero();
        assertThat(order.getRemark()).contains("63001");
        verify(remoteCourseService, never()).getCourse(any());
    }

    @Test
    void seckillOrderShouldBeRejectedWhenAlreadyPurchased() {
        when(orderMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> orderService.createSeckillOrder(USER, COURSE, new BigDecimal("49.00"), 63001L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已购买");
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    void seckillOrderShouldRejectNonPositivePrice() {
        assertThatThrownBy(() -> orderService.createSeckillOrder(USER, COURSE, BigDecimal.ZERO, 63001L))
                .isInstanceOf(ServiceException.class);
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    void previewShouldAlsoUseServerPrice() {
        when(remoteCourseService.getCourse(COURSE)).thenReturn(R.ok(course("199.00", 1, 1)));

        Order preview = orderService.previewOrder(USER, request());

        assertThat(preview.getAmount()).isEqualByComparingTo("199.00");
        verify(orderMapper, never()).insert(any(Order.class));
    }
}
