package com.zhixue.marketing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixue.common.core.exception.ServiceException;
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
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 秒杀服务实现类
 * 作用：处理秒杀活动相关的业务逻辑，比如预热库存、执行秒杀、查询在线活动等。
 * 它使用Redis和Lua脚本防止超卖，确保在高并发情况下库存数量的准确性。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillServiceImpl implements SeckillService {

    private final SeckillActivityMapper activityMapper;
    private final SeckillOrderMapper orderMapper;
    private final StringRedisTemplate redisTemplate;

    // Redis中存储库存的键前缀
    @Value("${marketing.seckill.stock-key-prefix:seckill:stock:}")
    private String stockKeyPrefix;

    // Redis中存储用户购买记录的键前缀
    @Value("${marketing.seckill.user-key-prefix:seckill:users:}")
    private String userKeyPrefix;

    // 秒杀用的Lua脚本对象
    private DefaultRedisScript<Long> seckillScript;

    /**
     * 加载秒杀用的Lua脚本
     * 作用：在服务启动时加载Redis执行的Lua脚本，用于秒杀时的原子操作，防止超卖。
     * Lua脚本可以保证多个Redis命令的原子执行，避免并发问题。
     */
    @PostConstruct  // 这个注解表示方法在服务启动时自动执行
    public void loadLua() {
        try {
            // 从classpath中读取Lua脚本文件
            ClassPathResource resource = new ClassPathResource("lua/seckill_stock.lua");
            String script = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            seckillScript = new DefaultRedisScript<>(script, Long.class);
        } catch (Exception e) {
            throw new IllegalStateException("加载秒杀 Lua 脚本失败", e);
        }
    }

    /**
     * 预热秒杀活动库存
     * 作用：把秒杀活动的库存数量加载到Redis中，这样秒杀时不用每次都查数据库，提高性能。
     * 只有未结束的活动才能预热库存。
     */
    @Override
    public void preloadStock(Long activityId) {
        // 查找秒杀活动信息
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new ServiceException("活动不存在");
        }
        // 检查活动是否已结束
        if (activity.getStatus() != null && activity.getStatus() == 2) {
            throw new ServiceException("活动已结束");
        }
        // 生成Redis中存储库存的键名
        String stockKey = stockKeyPrefix + activityId;
        // 把库存数量存到Redis
        redisTemplate.opsForValue().set(stockKey, String.valueOf(activity.getTotalStock()));
        log.info("活动 {} 预热库存 {} 成功", activityId, activity.getTotalStock());
    }

    /**
     * 执行秒杀操作
     * 作用：处理用户的秒杀请求，检查秒杀条件，执行库存扣减，生成秒杀订单。
     * 使用Redis和Lua脚本确保在高并发下不会超卖，也防止用户重复抢购。
     */
    @Override
    public String seckill(SeckillRequestDTO dto) {
        // 1. 检查秒杀活动是否存在
        SeckillActivity activity = activityMapper.selectById(dto.getActivityId());
        if (activity == null) {
            throw new ServiceException("活动不存在");
        }
        
        // 2. 检查活动是否在有效期内
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getStartTime())) {
            throw new ServiceException("活动未开始");
        }
        if (now.isAfter(activity.getEndTime())) {
            throw new ServiceException("活动已结束");
        }

        // 3. 生成Redis中存储库存和用户的键名
        String stockKey = stockKeyPrefix + dto.getActivityId();
        String userKey = userKeyPrefix + dto.getActivityId();
        
        // 4. 执行Lua脚本进行秒杀（原子操作，防止超卖和重复抢购）
        Long result = redisTemplate.execute(seckillScript,
                Collections.singletonList(stockKey),  // Lua脚本的键参数
                userKey, String.valueOf(dto.getUserId()));  // Lua脚本的其他参数
        
        // 5. 处理秒杀结果
        if (Objects.equals(result, 0L)) {
            throw new ServiceException("已售罄");
        }
        if (Objects.equals(result, 2L)) {
            throw new ServiceException("请勿重复抢购");
        }

        // 6. 生成秒杀订单
        SeckillOrder order = new SeckillOrder();
        order.setActivityId(dto.getActivityId());  // 设置活动ID
        order.setUserId(dto.getUserId());  // 设置用户ID
        order.setOrderNo(generateOrderNo());  // 生成订单号
        order.setStatus(0);  // 初始状态为未支付（0）
        orderMapper.insert(order);  // 保存到数据库
        
        return order.getOrderNo();
    }

    /**
     * 查询在线的秒杀活动列表
     * 作用：找出所有当前状态为进行中的秒杀活动，供用户查看和参与。
     */
    @Override
    public List<SeckillActivity> listOnline() {
        // 查询所有状态为1（进行中）的秒杀活动
        LambdaQueryWrapper<SeckillActivity> qw = new LambdaQueryWrapper<>();
        qw.eq(SeckillActivity::getStatus, 1);
        return activityMapper.selectList(qw);
    }

    /**
     * 生成秒杀订单号
     * 作用：生成一个唯一的秒杀订单号，用于标识用户的秒杀订单。
     * 订单号以"SK"开头，后面跟18位随机字符，确保唯一性。
     */
    private String generateOrderNo() {
        return "SK" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
    }
}


