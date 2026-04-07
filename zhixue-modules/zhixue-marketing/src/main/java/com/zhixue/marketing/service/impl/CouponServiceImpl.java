package com.zhixue.marketing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.domain.PageQuery;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.marketing.domain.dto.CouponApplyDTO;
import com.zhixue.marketing.domain.dto.CouponClaimDTO;
import com.zhixue.marketing.domain.dto.CouponCreateDTO;
import com.zhixue.marketing.domain.dto.CouponUpdateDTO;
import com.zhixue.marketing.domain.entity.Coupon;
import com.zhixue.marketing.domain.entity.CouponUser;
import com.zhixue.marketing.mapper.CouponMapper;
import com.zhixue.marketing.mapper.CouponUserMapper;
import com.zhixue.marketing.service.CouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 优惠券服务实现类。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private final CouponMapper couponMapper;
    private final CouponUserMapper couponUserMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Coupon create(CouponCreateDTO dto) {
        validateCouponTime(dto.getStartTime(), dto.getEndTime());
        Coupon coupon = new Coupon();
        coupon.setName(dto.getName());
        coupon.setTotalCount(dto.getTotalCount());
        coupon.setRemainCount(dto.getTotalCount());
        coupon.setDiscount(dto.getDiscount());
        coupon.setThresholdAmount(dto.getThresholdAmount());
        coupon.setStartTime(dto.getStartTime());
        coupon.setEndTime(dto.getEndTime());
        coupon.setStatus(resolveStatus(dto.getStartTime(), dto.getEndTime()));
        coupon.setUserLimit(dto.getUserLimit());
        couponMapper.insert(coupon);
        return coupon;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Coupon update(CouponUpdateDTO dto) {
        Coupon existing = requireCoupon(dto.getId());
        validateCouponTime(dto.getStartTime(), dto.getEndTime());
        if (dto.getTotalCount() < existing.getTotalCount() - existing.getRemainCount()) {
            throw new ServiceException("总数量不能小于已发放数量");
        }
        int usedCount = existing.getTotalCount() - existing.getRemainCount();
        existing.setName(dto.getName());
        existing.setTotalCount(dto.getTotalCount());
        existing.setRemainCount(Math.max(dto.getTotalCount() - usedCount, 0));
        existing.setDiscount(dto.getDiscount());
        existing.setThresholdAmount(dto.getThresholdAmount());
        existing.setStartTime(dto.getStartTime());
        existing.setEndTime(dto.getEndTime());
        existing.setStatus(dto.getStatus());
        existing.setUserLimit(dto.getUserLimit());
        couponMapper.updateById(existing);
        return existing;
    }

    @Override
    public PageResult<Coupon> pageCoupons(PageQuery query, String name, Integer status) {
        Page<Coupon> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<Coupon> qw = new LambdaQueryWrapper<>();
        qw.like(StringUtils.hasText(name), Coupon::getName, name)
                .eq(status != null, Coupon::getStatus, status)
                .orderByDesc(Coupon::getCreateTime);
        Page<Coupon> result = couponMapper.selectPage(page, qw);
        return PageResult.of(result.getRecords(), result.getTotal(), query.getPageSize());
    }

    @Override
    public List<Coupon> listAvailable() {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<Coupon> qw = new LambdaQueryWrapper<>();
        qw.eq(Coupon::getStatus, 1)
                .le(Coupon::getStartTime, now)
                .ge(Coupon::getEndTime, now)
                .gt(Coupon::getRemainCount, 0)
                .orderByDesc(Coupon::getCreateTime);
        return couponMapper.selectList(qw);
    }

    @Override
    public Coupon getById(Long couponId) {
        return couponMapper.selectById(couponId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CouponUser claim(CouponClaimDTO dto) {
        Coupon coupon = requireCoupon(dto.getCouponId());
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getStartTime())) {
            throw new ServiceException("优惠券未开始");
        }
        if (now.isAfter(coupon.getEndTime())) {
            throw new ServiceException("优惠券已结束");
        }
        if (coupon.getRemainCount() <= 0) {
            throw new ServiceException("已领完");
        }

        LambdaQueryWrapper<CouponUser> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(CouponUser::getCouponId, dto.getCouponId())
                .eq(CouponUser::getUserId, dto.getUserId());
        Long count = couponUserMapper.selectCount(countWrapper);
        if (count != null && count >= coupon.getUserLimit()) {
            throw new ServiceException("已达到领取上限");
        }

        LambdaUpdateWrapper<Coupon> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Coupon::getId, coupon.getId())
                .gt(Coupon::getRemainCount, 0)
                .setSql("remain_count = remain_count - 1");
        int updated = couponMapper.update(null, updateWrapper);
        if (updated <= 0) {
            throw new ServiceException("领取失败，请重试");
        }

        CouponUser record = new CouponUser();
        record.setCouponId(dto.getCouponId());
        record.setUserId(dto.getUserId());
        record.setStatus(0);
        record.setExpireTime(coupon.getEndTime());
        couponUserMapper.insert(record);
        return record;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(List<Long> couponIds) {
        if (CollectionUtils.isEmpty(couponIds)) {
            return;
        }
        couponMapper.deleteBatchIds(couponIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long couponId, Integer status) {
        Coupon coupon = requireCoupon(couponId);
        coupon.setStatus(status);
        couponMapper.updateById(coupon);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int issue(Long couponId, List<Long> userIds) {
        Coupon coupon = requireCoupon(couponId);
        if (CollectionUtils.isEmpty(userIds)) {
            return 0;
        }
        int success = 0;
        for (Long userId : userIds) {
            try {
                CouponClaimDTO dto = new CouponClaimDTO();
                dto.setCouponId(couponId);
                dto.setUserId(userId);
                claim(dto);
                success++;
            } catch (Exception e) {
                log.warn("发券失败 couponId={}, userId={}, msg={}", coupon.getId(), userId, e.getMessage());
            }
        }
        return success;
    }

    @Override
    public Map<String, Object> statistics(Long couponId) {
        Coupon coupon = requireCoupon(couponId);
        long issuedCount = coupon.getTotalCount() - coupon.getRemainCount();
        long usedCount = couponUserMapper.selectCount(new LambdaQueryWrapper<CouponUser>()
                .eq(CouponUser::getCouponId, couponId)
                .eq(CouponUser::getStatus, 1));
        long unusedCount = couponUserMapper.selectCount(new LambdaQueryWrapper<CouponUser>()
                .eq(CouponUser::getCouponId, couponId)
                .eq(CouponUser::getStatus, 0));
        long expiredCount = couponUserMapper.selectCount(new LambdaQueryWrapper<CouponUser>()
                .eq(CouponUser::getCouponId, couponId)
                .eq(CouponUser::getStatus, 2));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("couponId", couponId);
        data.put("totalCount", coupon.getTotalCount());
        data.put("remainCount", coupon.getRemainCount());
        data.put("issuedCount", issuedCount);
        data.put("usedCount", usedCount);
        data.put("unusedCount", unusedCount);
        data.put("expiredCount", expiredCount);
        data.put("usageRate", issuedCount == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(usedCount * 1.0d / issuedCount));
        return data;
    }

    @Override
    public List<CouponUser> listUserCoupons(Long userId, Integer status) {
        LambdaQueryWrapper<CouponUser> qw = new LambdaQueryWrapper<>();
        qw.eq(CouponUser::getUserId, userId)
                .eq(status != null, CouponUser::getStatus, status)
                .orderByDesc(CouponUser::getCreateTime);
        return couponUserMapper.selectList(qw);
    }

    @Override
    public BigDecimal applyCoupon(CouponApplyDTO dto) {
        if (dto.getOrderAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException("订单金额不合法");
        }
        CouponUser couponUser = couponUserMapper.selectById(dto.getCouponUserId());
        if (couponUser == null || !Objects.equals(couponUser.getUserId(), dto.getUserId())) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "优惠券记录不存在");
        }
        if (couponUser.getStatus() != 0) {
            throw new ServiceException("优惠券不可用");
        }
        Coupon coupon = requireCoupon(couponUser.getCouponId());
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(coupon.getEndTime())) {
            throw new ServiceException("优惠券已过期");
        }
        if (dto.getOrderAmount().compareTo(coupon.getThresholdAmount()) < 0) {
            throw new ServiceException("未达到优惠券使用门槛");
        }
        BigDecimal discount = coupon.getDiscount() == null ? BigDecimal.ZERO : coupon.getDiscount();
        if (discount.compareTo(dto.getOrderAmount()) > 0) {
            return dto.getOrderAmount();
        }
        return discount;
    }

    private Coupon requireCoupon(Long couponId) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "优惠券不存在");
        }
        return coupon;
    }

    private void validateCouponTime(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new ServiceException("优惠券结束时间必须晚于开始时间");
        }
    }

    private int resolveStatus(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(startTime)) {
            return 0;
        }
        if (now.isAfter(endTime)) {
            return 2;
        }
        return 1;
    }
}
