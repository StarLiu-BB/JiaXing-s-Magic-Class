package com.zhixue.marketing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.marketing.domain.dto.CouponClaimDTO;
import com.zhixue.marketing.domain.dto.CouponCreateDTO;
import com.zhixue.marketing.domain.entity.Coupon;
import com.zhixue.marketing.domain.entity.CouponUser;
import com.zhixue.marketing.mapper.CouponMapper;
import com.zhixue.marketing.mapper.CouponUserMapper;
import com.zhixue.marketing.service.CouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 优惠券服务实现类
 * 作用：处理优惠券相关的业务逻辑，比如创建优惠券、查询可用优惠券、领取优惠券等。
 * 它是营销模块中优惠券功能的核心实现，确保优惠券的发放和使用符合业务规则。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final CouponMapper couponMapper;
    private final CouponUserMapper couponUserMapper;

    /**
     * 创建优惠券
     * 作用：根据传入的参数创建一个新的优惠券，设置优惠券的基本信息，如名称、数量、优惠金额等。
     * 这个方法会把优惠券信息保存到数据库中。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)  // 这个注解表示方法需要事务保护，任何异常都会回滚操作
    public Coupon create(CouponCreateDTO dto) {
        Coupon coupon = new Coupon();
        coupon.setName(dto.getName());  // 设置优惠券名称
        coupon.setTotalCount(dto.getTotalCount());  // 设置总发行量
        coupon.setRemainCount(dto.getTotalCount());  // 剩余数量初始化为总发行量
        coupon.setDiscount(dto.getDiscount());  // 设置优惠金额
        coupon.setThresholdAmount(dto.getThresholdAmount());  // 设置使用门槛
        coupon.setStartTime(dto.getStartTime());  // 设置开始时间
        coupon.setEndTime(dto.getEndTime());  // 设置结束时间
        coupon.setStatus(0);  // 初始状态为未开始（0）
        coupon.setUserLimit(dto.getUserLimit());  // 设置每人最多领取数量
        couponMapper.insert(coupon);  // 保存到数据库
        return coupon;
    }

    /**
     * 查询可用的优惠券列表
     * 作用：找出所有当前时间可以使用的优惠券，即状态为生效中（1）且时间在有效期内的优惠券。
     */
    @Override
    public List<Coupon> listAvailable() {
        LocalDateTime now = LocalDateTime.now();
        // 查询所有状态为1（生效中）且开始时间<=当前时间<=结束时间的优惠券
        LambdaQueryWrapper<Coupon> qw = new LambdaQueryWrapper<>();
        qw.eq(Coupon::getStatus, 1)  // 状态为1表示生效中
          .le(Coupon::getStartTime, now)  // 开始时间<=当前时间
          .ge(Coupon::getEndTime, now);  // 结束时间>=当前时间
        return couponMapper.selectList(qw);
    }

    /**
     * 用户领取优惠券
     * 作用：处理用户领取优惠券的请求，检查领取条件是否满足，如优惠券是否存在、是否在有效期内、是否还有库存等。
     * 如果满足条件，就为用户创建一个优惠券记录，并减少优惠券的剩余数量。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)  // 这个注解表示方法需要事务保护，任何异常都会回滚操作
    public CouponUser claim(CouponClaimDTO dto) {
        // 1. 检查优惠券是否存在
        Coupon coupon = couponMapper.selectById(dto.getCouponId());
        if (coupon == null) {
            throw new ServiceException("优惠券不存在");
        }
        
        LocalDateTime now = LocalDateTime.now();
        // 2. 检查优惠券是否在有效期内
        if (now.isBefore(coupon.getStartTime())) {
            throw new ServiceException("优惠券未开始");
        }
        if (now.isAfter(coupon.getEndTime())) {
            throw new ServiceException("优惠券已结束");
        }
        // 3. 检查优惠券是否还有库存
        if (coupon.getRemainCount() <= 0) {
            throw new ServiceException("已领完");
        }
        
        // 4. 检查用户是否已达到领取上限
        LambdaQueryWrapper<CouponUser> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(CouponUser::getCouponId, dto.getCouponId())
                    .eq(CouponUser::getUserId, dto.getUserId());
        Long count = couponUserMapper.selectCount(countWrapper);
        if (count != null && count >= coupon.getUserLimit()) {
            throw new ServiceException("已达到领取上限");
        }

        // 5. 乐观扣减优惠券剩余数量（防止并发领取导致超发）
        LambdaQueryWrapper<Coupon> updateWrapper = new LambdaQueryWrapper<>();
        updateWrapper.eq(Coupon::getId, coupon.getId())
                     .gt(Coupon::getRemainCount, 0);  // 只有剩余数量>0时才更新
        coupon.setRemainCount(coupon.getRemainCount() - 1);
        int updated = couponMapper.update(coupon, updateWrapper);
        if (updated <= 0) {
            throw new ServiceException("领取失败，请重试");
        }

        // 6. 为用户创建优惠券记录
        CouponUser record = new CouponUser();
        record.setCouponId(dto.getCouponId());  // 设置优惠券ID
        record.setUserId(dto.getUserId());  // 设置用户ID
        record.setStatus(0);  // 初始状态为未使用（0）
        record.setExpireTime(coupon.getEndTime());  // 设置过期时间
        couponUserMapper.insert(record);  // 保存到数据库
        return record;
    }
}

