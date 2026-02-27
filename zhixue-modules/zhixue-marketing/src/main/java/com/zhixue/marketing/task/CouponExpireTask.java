package com.zhixue.marketing.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.zhixue.marketing.domain.entity.CouponUser;
import com.zhixue.marketing.mapper.CouponUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 优惠券过期定时任务
 * 作用：定期检查并更新已过期的优惠券状态，把用户未使用但已过期的优惠券标记为已过期。
 * 它使用XXL-Job定时任务框架，定时执行优惠券过期处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponExpireTask {

    private final CouponUserMapper couponUserMapper;

    /**
     * 执行优惠券过期处理
     * 作用：找出所有未使用且已过期的优惠券，把它们的状态更新为已过期（状态码2）。
     * 这样用户就无法再使用已过期的优惠券了，同时系统可以统计过期优惠券的数量。
     */
    @XxlJob("couponExpireHandler")  // 这个注解表示这是一个XXL-Job定时任务
    public void expireJob() {
        LocalDateTime now = LocalDateTime.now();
        // 查询所有状态为0（未使用）且过期时间在当前时间之前的优惠券
        LambdaQueryWrapper<CouponUser> qw = new LambdaQueryWrapper<>();
        qw.eq(CouponUser::getStatus, 0)  // 状态为0表示未使用
          .lt(CouponUser::getExpireTime, now);  // 过期时间在当前时间之前
        List<CouponUser> list = couponUserMapper.selectList(qw);
        
        // 把每个过期的优惠券状态更新为已过期（状态码2）
        list.forEach(cu -> {
            cu.setStatus(2);  // 状态为2表示已过期
            couponUserMapper.updateById(cu);  // 更新到数据库
        });
        
        log.info("优惠券过期任务完成，处理数量={}", list.size());
    }
}


