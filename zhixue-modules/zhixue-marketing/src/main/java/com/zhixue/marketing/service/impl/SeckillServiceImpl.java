package com.zhixue.marketing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.domain.PageQuery;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.marketing.domain.dto.SeckillActivityDTO;
import com.zhixue.marketing.domain.dto.SeckillRequestDTO;
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
import java.util.Collections;
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

    private final SeckillActivityMapper activityMapper;
    private final SeckillOrderMapper orderMapper;
    private final StringRedisTemplate redisTemplate;

    @Value("${marketing.seckill.stock-key-prefix:seckill:stock:}")
    private String stockKeyPrefix;

    @Value("${marketing.seckill.user-key-prefix:seckill:users:}")
    private String userKeyPrefix;

    @Value("${marketing.seckill.token-key-prefix:seckill:token:}")
    private String tokenKeyPrefix;

    @Value("${zhixue.integration.marketing.mode:sandbox}")
    private String marketingMode;

    private DefaultRedisScript<Long> seckillScript;

    @PostConstruct
    public void loadLua() {
        try {
            ClassPathResource resource = new ClassPathResource("lua/seckill_stock.lua");
            String script = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            seckillScript = new DefaultRedisScript<>(script, Long.class);
        } catch (Exception e) {
            throw new IllegalStateException("加载秒杀 Lua 脚本失败", e);
        }
    }

    @Override
    public void preloadStock(Long activityId) {
        SeckillActivity activity = requireActivity(activityId);
        if (activity.getStatus() != null && activity.getStatus() == 2) {
            throw new ServiceException("活动已结束");
        }
        redisTemplate.opsForValue().set(stockKeyPrefix + activityId, String.valueOf(activity.getTotalStock()));
        log.info("活动库存预热成功 activityId={}, stock={}", activityId, activity.getTotalStock());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String seckill(SeckillRequestDTO dto) {
        SeckillActivity activity = requireActivity(dto.getActivityId());
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getStartTime())) {
            throw new ServiceException("活动未开始");
        }
        if (now.isAfter(activity.getEndTime())) {
            throw new ServiceException("活动已结束");
        }
        validateToken(dto);

        String stockKey = stockKeyPrefix + dto.getActivityId();
        String userKey = userKeyPrefix + dto.getActivityId();
        Long result = redisTemplate.execute(seckillScript, Collections.singletonList(stockKey), userKey, String.valueOf(dto.getUserId()));
        if (Objects.equals(result, 0L)) {
            throw new ServiceException("已售罄");
        }
        if (Objects.equals(result, 2L)) {
            throw new ServiceException("请勿重复抢购");
        }

        SeckillOrder order = new SeckillOrder();
        order.setActivityId(dto.getActivityId());
        order.setUserId(dto.getUserId());
        order.setOrderNo(generateOrderNo());
        order.setStatus(0);
        orderMapper.insert(order);
        return order.getOrderNo();
    }

    @Override
    public List<SeckillActivity> listOnline() {
        LambdaQueryWrapper<SeckillActivity> qw = new LambdaQueryWrapper<>();
        qw.eq(SeckillActivity::getStatus, 1)
                .orderByAsc(SeckillActivity::getStartTime);
        return activityMapper.selectList(qw);
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
        SeckillActivity activity = requireActivity(activityId);
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(activity.getEndTime())) {
            throw new ServiceException("活动已结束");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        Duration ttl = Duration.ofMinutes(10);
        if (activity.getEndTime().isAfter(now)) {
            long seconds = Duration.between(now, activity.getEndTime()).toSeconds();
            ttl = Duration.ofSeconds(Math.max(30, Math.min(seconds, ttl.toSeconds())));
        }
        redisTemplate.opsForValue().set(tokenKey(activityId, userId), token, ttl);
        return token;
    }

    private void validateToken(SeckillRequestDTO dto) {
        if ("sandbox".equalsIgnoreCase(marketingMode) && !StringUtils.hasText(dto.getToken())) {
            return;
        }
        if (!StringUtils.hasText(dto.getToken())) {
            throw new ServiceException("秒杀令牌不能为空");
        }
        String key = tokenKey(dto.getActivityId(), dto.getUserId());
        String cached = redisTemplate.opsForValue().get(key);
        if (!dto.getToken().equals(cached)) {
            throw new ServiceException("秒杀令牌无效或已过期");
        }
        redisTemplate.delete(key);
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

    private String tokenKey(Long activityId, Long userId) {
        return tokenKeyPrefix + activityId + ":" + userId;
    }

    private String generateOrderNo() {
        return "SK" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
    }
}
