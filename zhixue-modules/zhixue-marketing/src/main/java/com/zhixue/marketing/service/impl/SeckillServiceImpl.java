package com.zhixue.marketing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixue.api.order.RemoteOrderService;
import com.zhixue.api.order.domain.OrderInfo;
import com.zhixue.api.order.domain.SeckillOrderRequest;
import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.domain.PageQuery;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.security.config.SecurityProperties;
import com.zhixue.marketing.domain.dto.SeckillActivityDTO;
import com.zhixue.marketing.domain.dto.SeckillRequestDTO;
import com.zhixue.marketing.domain.dto.SeckillResultVO;
import com.zhixue.marketing.domain.entity.SeckillActivity;
import com.zhixue.marketing.domain.entity.SeckillOrder;
import com.zhixue.marketing.mapper.SeckillActivityMapper;
import com.zhixue.marketing.mapper.SeckillOrderMapper;
import com.zhixue.marketing.service.SeckillService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 秒杀服务实现类。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillServiceImpl implements SeckillService {

    private static final int ACTIVITY_ONLINE = 1;
    /** seckill_order.status：0 已锁定（历史数据） 1 已下单 2 已取消 */
    private static final int RECORD_ORDERED = 1;

    private final SeckillActivityMapper activityMapper;
    private final SeckillOrderMapper orderMapper;
    private final StringRedisTemplate redisTemplate;
    private final RemoteOrderService remoteOrderService;
    private final SecurityProperties securityProperties;

    @Value("${marketing.seckill.stock-key-prefix:seckill:stock:}")
    private String stockKeyPrefix;

    @Value("${marketing.seckill.user-key-prefix:seckill:users:}")
    private String userKeyPrefix;

    @Value("${marketing.seckill.token-key-prefix:seckill:token:}")
    private String tokenKeyPrefix;

    private DefaultRedisScript<Long> seckillScript;
    private DefaultRedisScript<Long> rollbackScript;

    @PostConstruct
    public void loadLua() {
        seckillScript = loadScript("lua/seckill_stock.lua");
        rollbackScript = loadScript("lua/seckill_rollback.lua");
    }

    private DefaultRedisScript<Long> loadScript(String location) {
        try {
            ClassPathResource resource = new ClassPathResource(location);
            String script = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            return new DefaultRedisScript<>(script, Long.class);
        } catch (Exception e) {
            throw new IllegalStateException("加载秒杀 Lua 脚本失败: " + location, e);
        }
    }

    /**
     * 补偿：归还 Redis 库存并移除已抢购标记。
     *
     * <p>Redis 扣减不在数据库事务范围内，事务回滚不会撤销它，
     * 必须显式补偿，否则库存永久丢失、用户永久无法再抢。</p>
     */
    private void rollbackStock(String stockKey, String userKey, Long userId) {
        try {
            redisTemplate.execute(rollbackScript, List.of(stockKey, userKey), String.valueOf(userId));
            log.warn("秒杀下单失败，已回滚 Redis 库存 userId={}, stockKey={}", userId, stockKey);
        } catch (Exception e) {
            // 补偿失败需要告警人工介入：此时 Redis 与 DB 已不一致
            log.error("秒杀库存回滚失败，需人工核对 userId={}, stockKey={}", userId, stockKey, e);
        }
    }

    @Override
    public void preloadStock(Long activityId) {
        SeckillActivity activity = requireActivity(activityId);
        if (activity.getStatus() != null && activity.getStatus() == 2) {
            throw new ServiceException("活动已结束");
        }
        ensureStockLoaded(activity);
    }

    /**
     * 按"总库存 - 已成交"装载 Redis 库存，且只在 key 不存在时写入（SETNX）。
     *
     * <p>修复前直接 SET totalStock：活动进行中重复预热会把已售出的库存加回来导致超卖。
     * 同时在签发令牌、展示列表时惰性调用，使进行中的活动无需 XXL-Job 也能正常开抢
     * （调度器默认关闭，且只预热未开始的活动）。</p>
     */
    private void ensureStockLoaded(SeckillActivity activity) {
        long sold = orderMapper.selectCount(new LambdaQueryWrapper<SeckillOrder>()
                .eq(SeckillOrder::getActivityId, activity.getId())
                .in(SeckillOrder::getStatus, 0, RECORD_ORDERED));
        int total = activity.getTotalStock() == null ? 0 : activity.getTotalStock();
        long remaining = Math.max(0, total - sold);
        Boolean loaded = redisTemplate.opsForValue()
                .setIfAbsent(stockKeyPrefix + activity.getId(), String.valueOf(remaining));
        if (Boolean.TRUE.equals(loaded)) {
            log.info("活动库存已装载 activityId={}, total={}, sold={}, remaining={}",
                    activity.getId(), total, sold, remaining);
        }
    }

    /**
     * 执行秒杀。成交即在订单服务生成真实待支付订单（按秒杀价），客户端直接进入收银台。
     *
     * <p>未引入 MQ：Lua 已保证进入下单环节的请求数不超过库存数，数据库写入量被 Redis 挡住，
     * 同步下单即可，且客户端无需轮询结果。</p>
     *
     * <p>已知残留风险：若订单服务已建单但响应超时，本地会回滚库存，产生一张按秒杀价的
     * 孤儿待支付单（最多多卖该笔）。彻底解决需要订单服务按 (activityId, userId) 做幂等键。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SeckillResultVO seckill(Long userId, SeckillRequestDTO dto) {
        SeckillActivity activity = requireOnlineActivity(dto.getActivityId());
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getStartTime())) {
            throw new ServiceException("活动未开始");
        }
        if (now.isAfter(activity.getEndTime())) {
            throw new ServiceException("活动已结束");
        }
        validateToken(userId, dto);

        String stockKey = stockKeyPrefix + dto.getActivityId();
        String userKey = userKeyPrefix + dto.getActivityId();
        // 两个 key 都通过 KEYS 传入，满足 Redis Cluster 的槽位校验要求
        Long result = redisTemplate.execute(seckillScript, List.of(stockKey, userKey),
                String.valueOf(userId));
        if (Objects.equals(result, 0L)) {
            throw new ServiceException("已售罄");
        }
        if (Objects.equals(result, 2L)) {
            throw new ServiceException("请勿重复抢购");
        }

        // 库存已在 Redis 中扣减，后续任何失败都必须补偿回滚，
        // 否则事务回滚只撤销数据库，Redis 库存永久丢失。
        try {
            OrderInfo realOrder = createRealOrder(userId, activity);
            SeckillOrder record = new SeckillOrder();
            record.setActivityId(dto.getActivityId());
            record.setUserId(userId);
            record.setOrderNo(realOrder.getOrderNo());
            record.setStatus(RECORD_ORDERED);
            orderMapper.insert(record);
            return new SeckillResultVO(realOrder.getOrderNo(), realOrder.getAmount());
        } catch (RuntimeException e) {
            rollbackStock(stockKey, userKey, userId);
            throw e;
        }
    }

    private OrderInfo createRealOrder(Long userId, SeckillActivity activity) {
        SeckillOrderRequest request = new SeckillOrderRequest();
        request.setUserId(userId);
        request.setProductId(activity.getProductId());
        request.setAmount(activity.getSeckillPrice());
        request.setActivityId(activity.getId());
        R<OrderInfo> result;
        try {
            result = remoteOrderService.createSeckillOrder(request, "true", securityProperties.getInternalToken());
        } catch (Exception e) {
            log.error("秒杀成交后调用订单服务建单失败 userId={}, activityId={}", userId, activity.getId(), e);
            throw new ServiceException(HttpStatus.SERVICE_UNAVAILABLE, "下单繁忙，请稍后重试");
        }
        if (result == null || !result.isSuccess() || result.getData() == null) {
            // 订单服务的业务拒绝（如"已购买该课程"）原样告知用户
            throw new ServiceException(HttpStatus.BAD_REQUEST,
                    result == null || !StringUtils.hasText(result.getMsg()) ? "下单失败" : result.getMsg());
        }
        return result.getData();
    }

    @Override
    public List<SeckillActivity> listOnline() {
        LambdaQueryWrapper<SeckillActivity> qw = new LambdaQueryWrapper<>();
        qw.eq(SeckillActivity::getStatus, ACTIVITY_ONLINE)
                .orderByAsc(SeckillActivity::getStartTime);
        List<SeckillActivity> activities = activityMapper.selectList(qw);
        for (SeckillActivity activity : activities) {
            ensureStockLoaded(activity);
            String stock = redisTemplate.opsForValue().get(stockKeyPrefix + activity.getId());
            activity.setRemainingStock(stock == null ? null : Integer.valueOf(stock));
        }
        return activities;
    }

    @Override
    public PageResult<SeckillActivity> pageActivities(PageQuery query, String title, Integer status) {
        Page<SeckillActivity> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SeckillActivity> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(title), SeckillActivity::getTitle, title)
                .eq(status != null, SeckillActivity::getStatus, status)
                .orderByDesc(SeckillActivity::getCreateTime);
        Page<SeckillActivity> result = activityMapper.selectPage(page, qw);
        return PageResult.of(result.getRecords(), result.getTotal(), query.getPageSize());
    }

    @Override
    public SeckillActivity getById(Long id) {
        return activityMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SeckillActivity create(SeckillActivityDTO dto) {
        validateActivityTime(dto.getStartTime(), dto.getEndTime());
        SeckillActivity activity = new SeckillActivity();
        fillActivity(activity, dto);
        activityMapper.insert(activity);
        return activity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SeckillActivity update(SeckillActivityDTO dto) {
        if (dto.getId() == null) {
            throw new ServiceException("活动ID不能为空");
        }
        validateActivityTime(dto.getStartTime(), dto.getEndTime());
        SeckillActivity activity = requireActivity(dto.getId());
        fillActivity(activity, dto);
        activityMapper.updateById(activity);
        return activity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        activityMapper.deleteBatchIds(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, Integer status) {
        SeckillActivity activity = requireActivity(id);
        activity.setStatus(status);
        activityMapper.updateById(activity);
    }

    @Override
    public Map<String, Object> statistics(Long id) {
        SeckillActivity activity = requireActivity(id);
        long totalOrderCount = orderMapper.selectCount(new LambdaQueryWrapper<SeckillOrder>()
                .eq(SeckillOrder::getActivityId, id));
        long lockedCount = orderMapper.selectCount(new LambdaQueryWrapper<SeckillOrder>()
                .eq(SeckillOrder::getActivityId, id)
                .eq(SeckillOrder::getStatus, 0));
        long createdCount = orderMapper.selectCount(new LambdaQueryWrapper<SeckillOrder>()
                .eq(SeckillOrder::getActivityId, id)
                .eq(SeckillOrder::getStatus, 1));
        long canceledCount = orderMapper.selectCount(new LambdaQueryWrapper<SeckillOrder>()
                .eq(SeckillOrder::getActivityId, id)
                .eq(SeckillOrder::getStatus, 2));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("activityId", id);
        data.put("title", activity.getTitle());
        data.put("totalStock", activity.getTotalStock());
        data.put("totalOrderCount", totalOrderCount);
        data.put("lockedCount", lockedCount);
        data.put("createdCount", createdCount);
        data.put("canceledCount", canceledCount);
        return data;
    }

    @Override
    public String issueToken(Long activityId, Long userId) {
        SeckillActivity activity = requireOnlineActivity(activityId);
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(activity.getEndTime())) {
            throw new ServiceException("活动已结束");
        }
        ensureStockLoaded(activity);
        String token = UUID.randomUUID().toString().replace("-", "");
        Duration ttl = Duration.ofMinutes(10);
        if (activity.getEndTime().isAfter(now)) {
            long seconds = Duration.between(now, activity.getEndTime()).toSeconds();
            ttl = Duration.ofSeconds(Math.max(30, Math.min(seconds, ttl.toSeconds())));
        }
        redisTemplate.opsForValue().set(tokenKey(activityId, userId), token, ttl);
        return token;
    }

    /**
     * 令牌一律必填：修复前 sandbox（默认）模式下不带令牌即放行，令牌形同虚设。
     * 令牌按 activityId + 登录用户存储，拿别人的令牌无效。
     *
     * <p>必须用 GETDEL 原子取出并删除：原先 GET 后再 DEL，两步之间存在窗口，
     * 压测中同一令牌并发重放 10 次有 2 次通过校验（后被 Lua 的用户集合兜住）。</p>
     */
    private void validateToken(Long userId, SeckillRequestDTO dto) {
        if (!StringUtils.hasText(dto.getToken())) {
            throw new ServiceException("秒杀令牌不能为空");
        }
        String cached = redisTemplate.opsForValue().getAndDelete(tokenKey(dto.getActivityId(), userId));
        if (!dto.getToken().equals(cached)) {
            throw new ServiceException("秒杀令牌无效或已过期");
        }
    }

    private void fillActivity(SeckillActivity activity, SeckillActivityDTO dto) {
        activity.setTitle(dto.getTitle());
        activity.setProductId(dto.getProductId());
        activity.setSeckillPrice(dto.getSeckillPrice());
        activity.setTotalStock(dto.getTotalStock());
        activity.setStartTime(dto.getStartTime());
        activity.setEndTime(dto.getEndTime());
        activity.setStatus(dto.getStatus());
    }

    private void validateActivityTime(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new ServiceException("活动结束时间必须晚于开始时间");
        }
    }

    private SeckillActivity requireActivity(Long activityId) {
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "活动不存在");
        }
        return activity;
    }

    private SeckillActivity requireOnlineActivity(Long activityId) {
        SeckillActivity activity = requireActivity(activityId);
        if (!Integer.valueOf(ACTIVITY_ONLINE).equals(activity.getStatus())) {
            throw new ServiceException("活动未上线");
        }
        return activity;
    }

    private String tokenKey(Long activityId, Long userId) {
        return tokenKeyPrefix + activityId + ":" + userId;
    }
}
