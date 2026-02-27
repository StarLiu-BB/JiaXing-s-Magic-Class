package com.zhixue.marketing.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.zhixue.marketing.domain.entity.SeckillActivity;
import com.zhixue.marketing.mapper.SeckillActivityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀库存预热任务
 * 作用：提前把即将开始的秒杀活动的库存加载到Redis里，这样秒杀时不用每次都查数据库，提高性能。
 * 它使用XXL-Job定时任务框架，定时执行库存预热操作。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillPreloadTask {

    private final SeckillActivityMapper activityMapper;
    private final StringRedisTemplate redisTemplate;

    /**
     * 执行库存预热操作
     * 作用：找出所有即将开始的秒杀活动，把它们的库存数量存到Redis中。
     * 这样用户秒杀时，系统可以直接从Redis读取库存，减少数据库压力，提高响应速度。
     */
    @XxlJob("seckillPreloadHandler")  // 这个注解表示这是一个XXL-Job定时任务
    public void preload() {
        LocalDateTime now = LocalDateTime.now();
        // 查询所有状态为0（未开始）且开始时间在当前时间之后的秒杀活动
        LambdaQueryWrapper<SeckillActivity> qw = new LambdaQueryWrapper<>();
        qw.eq(SeckillActivity::getStatus, 0)  // 状态为0表示未开始
          .gt(SeckillActivity::getStartTime, now);  // 开始时间在当前时间之后
        List<SeckillActivity> list = activityMapper.selectList(qw);
        
        // 把每个活动的库存加载到Redis
        list.forEach(act -> {
            String key = "seckill:stock:" + act.getId();  // Redis中存储库存的键名
            redisTemplate.opsForValue().set(key, String.valueOf(act.getTotalStock()));  // 存储库存数量
        });
        
        log.info("秒杀预热任务完成，预热活动数={}", list.size());
    }
}


