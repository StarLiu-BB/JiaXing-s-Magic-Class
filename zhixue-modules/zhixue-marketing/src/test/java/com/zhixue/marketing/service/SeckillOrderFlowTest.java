package com.zhixue.marketing.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixue.api.order.RemoteOrderService;
import com.zhixue.api.order.domain.OrderInfo;
import com.zhixue.api.order.domain.SeckillOrderRequest;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.security.config.SecurityProperties;
import com.zhixue.marketing.domain.dto.SeckillRequestDTO;
import com.zhixue.marketing.domain.dto.SeckillResultVO;
import com.zhixue.marketing.domain.entity.SeckillActivity;
import com.zhixue.marketing.domain.entity.SeckillOrder;
import com.zhixue.marketing.mapper.SeckillActivityMapper;
import com.zhixue.marketing.mapper.SeckillOrderMapper;
import com.zhixue.marketing.service.impl.SeckillServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 秒杀下单链路测试。
 *
 * <p>修复前实测：抢到后只在营销库写一条 SK 开头的记录，订单服务中不存在该订单，
 * 用户抢到了却无法付款；userId 由请求体决定，sandbox 模式下不带令牌也能直接秒杀。</p>
 */
@ExtendWith(MockitoExtension.class)
class SeckillOrderFlowTest {

    private static final long USER = 1003L;
    private static final long ACTIVITY = 63001L;
    private static final String TOKEN = "internal-token-for-unit-test-0123456789";

    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SeckillOrder.class);
        TableInfoHelper.initTableInfo(assistant, SeckillActivity.class);
    }

    @Mock
    private SeckillActivityMapper activityMapper;
    @Mock
    private SeckillOrderMapper orderMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private RemoteOrderService remoteOrderService;

    private SeckillServiceImpl service;

    @BeforeEach
    void setUp() {
        SecurityProperties properties = new SecurityProperties();
        properties.setInternalToken(TOKEN);
        service = new SeckillServiceImpl(activityMapper, orderMapper, redisTemplate, remoteOrderService, properties);
        ReflectionTestUtils.setField(service, "stockKeyPrefix", "seckill:stock:");
        ReflectionTestUtils.setField(service, "userKeyPrefix", "seckill:users:");
        ReflectionTestUtils.setField(service, "tokenKeyPrefix", "seckill:token:");
        service.loadLua();
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(activityMapper.selectById(ACTIVITY)).thenReturn(activity(1));
    }

    private SeckillActivity activity(int status) {
        SeckillActivity activity = new SeckillActivity();
        activity.setId(ACTIVITY);
        activity.setProductId(2004L);
        activity.setSeckillPrice(new BigDecimal("49.00"));
        activity.setTotalStock(50);
        activity.setStatus(status);
        activity.setStartTime(LocalDateTime.now().minusHours(1));
        activity.setEndTime(LocalDateTime.now().plusHours(1));
        return activity;
    }

    private SeckillRequestDTO request(String token) {
        SeckillRequestDTO dto = new SeckillRequestDTO();
        dto.setActivityId(ACTIVITY);
        dto.setToken(token);
        return dto;
    }

    private static ArgumentMatcher<RedisScript<Long>> scriptContaining(String op) {
        return script -> script != null && script.getScriptAsString().contains(op);
    }

    private void tokenIsValid() {
        when(valueOps.getAndDelete("seckill:token:" + ACTIVITY + ":" + USER)).thenReturn("tk");
    }

    private void luaDeductSucceeds() {
        when(redisTemplate.execute(argThat(scriptContaining("DECR")), anyList(), eq(String.valueOf(USER)))).thenReturn(1L);
    }

    private OrderInfo orderInfo() {
        OrderInfo info = new OrderInfo();
        info.setOrderNo("ORD-SK-1");
        info.setAmount(new BigDecimal("49.00"));
        info.setStatus(0);
        return info;
    }

    @Test
    void tokenIsMandatoryEvenInSandbox() {
        // 修复前 sandbox（默认）模式不带令牌即放行，令牌形同虚设
        assertThatThrownBy(() -> service.seckill(USER, request(null)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("令牌");
        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), anyString());
    }

    @Test
    void tokenIssuedForAnotherUserMustNotWork() {
        // 令牌按 activityId + 登录用户存储；拿别人的令牌无效
        when(valueOps.getAndDelete("seckill:token:" + ACTIVITY + ":" + USER)).thenReturn(null);

        assertThatThrownBy(() -> service.seckill(USER, request("someone-elses-token")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("令牌无效");
    }

    @Test
    void tokenMustBeConsumedAtomically() {
        // 压测实测：先 GET 再 DEL 两步之间存在窗口，同一令牌并发重放 10 次有 2 次通过校验。
        // 必须用 GETDEL 原子取出并删除，令牌才真正"一次性"
        tokenIsValid();
        luaDeductSucceeds();
        when(remoteOrderService.createSeckillOrder(any(SeckillOrderRequest.class), anyString(), anyString()))
                .thenReturn(R.ok(orderInfo()));

        service.seckill(USER, request("tk"));

        verify(valueOps).getAndDelete("seckill:token:" + ACTIVITY + ":" + USER);
        verify(valueOps, never()).get("seckill:token:" + ACTIVITY + ":" + USER);
        verify(redisTemplate, never()).delete("seckill:token:" + ACTIVITY + ":" + USER);
    }

    @Test
    void offlineActivityMustBeRejected() {
        when(activityMapper.selectById(ACTIVITY)).thenReturn(activity(0));

        assertThatThrownBy(() -> service.seckill(USER, request("tk")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("未上线");
    }

    @Test
    void successShouldCreatePayableOrderInOrderServiceAtSeckillPrice() {
        tokenIsValid();
        luaDeductSucceeds();
        when(remoteOrderService.createSeckillOrder(any(SeckillOrderRequest.class), eq("true"), eq(TOKEN)))
                .thenReturn(R.ok(orderInfo()));

        SeckillResultVO result = service.seckill(USER, request("tk"));

        ArgumentCaptor<SeckillOrderRequest> sent = ArgumentCaptor.forClass(SeckillOrderRequest.class);
        verify(remoteOrderService).createSeckillOrder(sent.capture(), eq("true"), eq(TOKEN));
        assertThat(sent.getValue().getUserId()).isEqualTo(USER);
        assertThat(sent.getValue().getProductId()).isEqualTo(2004L);
        assertThat(sent.getValue().getAmount()).isEqualByComparingTo("49.00");
        assertThat(sent.getValue().getActivityId()).isEqualTo(ACTIVITY);

        // 客户端拿到的是订单服务的真实订单号，可直接进入收银台
        assertThat(result.getOrderNo()).isEqualTo("ORD-SK-1");
        assertThat(result.getAmount()).isEqualByComparingTo("49.00");

        ArgumentCaptor<SeckillOrder> record = ArgumentCaptor.forClass(SeckillOrder.class);
        verify(orderMapper).insert(record.capture());
        assertThat(record.getValue().getOrderNo()).isEqualTo("ORD-SK-1");
        assertThat(record.getValue().getUserId()).isEqualTo(USER);
        assertThat(record.getValue().getStatus()).as("1=已下单").isEqualTo(1);
    }

    @Test
    void orderServiceRejectionShouldRollbackStock() {
        tokenIsValid();
        luaDeductSucceeds();
        when(remoteOrderService.createSeckillOrder(any(SeckillOrderRequest.class), anyString(), anyString()))
                .thenReturn(R.fail(400, "您已购买该课程，无需参与秒杀"));

        assertThatThrownBy(() -> service.seckill(USER, request("tk")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已购买");

        verify(redisTemplate).execute(argThat(scriptContaining("INCR")), anyList(), eq(String.valueOf(USER)));
        verify(orderMapper, never()).insert(any(SeckillOrder.class));
    }

    @Test
    void orderServiceFailureShouldRollbackStock() {
        tokenIsValid();
        luaDeductSucceeds();
        when(remoteOrderService.createSeckillOrder(any(SeckillOrderRequest.class), anyString(), anyString()))
                .thenThrow(new RuntimeException("connect timed out"));

        assertThatThrownBy(() -> service.seckill(USER, request("tk")))
                .isInstanceOf(ServiceException.class);

        verify(redisTemplate).execute(argThat(scriptContaining("INCR")), anyList(), eq(String.valueOf(USER)));
        verify(orderMapper, never()).insert(any(SeckillOrder.class));
    }

    @Test
    void preloadMustNotResetStockThatIsAlreadySelling() {
        // 修复前 preload 直接 SET totalStock：活动进行中重复预热会把已售出的库存加回来，导致超卖
        when(orderMapper.selectCount(any())).thenReturn(3L);

        service.preloadStock(ACTIVITY);

        verify(valueOps).setIfAbsent("seckill:stock:" + ACTIVITY, "47");
        verify(valueOps, never()).set(eq("seckill:stock:" + ACTIVITY), anyString());
    }

    @Test
    void issuingTokenShouldLoadStockLazilySoSeckillWorksWithoutScheduler() {
        // XXL-Job 默认关闭且只预热未开始的活动；进行中的活动首次访问时按剩余量装载
        when(orderMapper.selectCount(any())).thenReturn(0L);

        service.issueToken(ACTIVITY, USER);

        verify(valueOps).setIfAbsent("seckill:stock:" + ACTIVITY, "50");
    }
}
