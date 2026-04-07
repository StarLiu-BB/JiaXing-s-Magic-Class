package com.zhixue.marketing.controller;

import com.zhixue.common.core.domain.PageQuery;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.domain.R;
import com.zhixue.marketing.domain.dto.CouponApplyDTO;
import com.zhixue.marketing.domain.dto.CouponClaimDTO;
import com.zhixue.marketing.domain.dto.CouponCreateDTO;
import com.zhixue.marketing.domain.dto.CouponIssueDTO;
import com.zhixue.marketing.domain.dto.CouponStatusDTO;
import com.zhixue.marketing.domain.dto.CouponUpdateDTO;
import com.zhixue.marketing.domain.entity.Coupon;
import com.zhixue.marketing.domain.entity.CouponUser;
import com.zhixue.marketing.service.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 优惠券接口控制器。
 */
@RestController
@RequestMapping("/coupon")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @PostMapping
    public R<Coupon> create(@Valid @RequestBody CouponCreateDTO dto) {
        return R.ok(couponService.create(dto));
    }

    @PostMapping("/create")
    public R<Coupon> createAlias(@Valid @RequestBody CouponCreateDTO dto) {
        return R.ok(couponService.create(dto));
    }

    @PutMapping
    public R<Coupon> update(@Valid @RequestBody CouponUpdateDTO dto) {
        return R.ok(couponService.update(dto));
    }

    @GetMapping("/list")
    public R<PageResult<Coupon>> list(PageQuery pageQuery,
                                      @RequestParam(required = false) String name,
                                      @RequestParam(required = false) Integer status) {
        return R.ok(couponService.pageCoupons(pageQuery, name, status));
    }

    @GetMapping("/{couponId}")
    public R<Coupon> get(@PathVariable Long couponId) {
        return R.ok(couponService.getById(couponId));
    }

    @DeleteMapping("/{couponIds}")
    public R<Void> delete(@PathVariable String couponIds) {
        List<Long> ids = Arrays.stream(couponIds.split(","))
                .filter(StringUtils::hasText)
                .map(Long::valueOf)
                .toList();
        couponService.deleteByIds(ids);
        return R.ok();
    }

    @PutMapping("/{couponId}/status")
    public R<Void> changeStatus(@PathVariable Long couponId, @Valid @RequestBody CouponStatusDTO dto) {
        couponService.changeStatus(couponId, dto.getStatus());
        return R.ok();
    }

    @PostMapping("/{couponId}/issue")
    public R<Integer> issue(@PathVariable Long couponId, @RequestBody(required = false) CouponIssueDTO dto) {
        List<Long> userIds = dto == null ? null : dto.getUserIds();
        return R.ok(couponService.issue(couponId, userIds));
    }

    @GetMapping("/{couponId}/statistics")
    public R<Map<String, Object>> statistics(@PathVariable Long couponId) {
        return R.ok(couponService.statistics(couponId));
    }

    @GetMapping("/available")
    public R<List<Coupon>> listAvailable() {
        return R.ok(couponService.listAvailable());
    }

    @PostMapping("/claim")
    public R<CouponUser> claim(@Valid @RequestBody CouponClaimDTO dto) {
        return R.ok(couponService.claim(dto));
    }

    @GetMapping("/user/list")
    public R<List<CouponUser>> listUserCoupons(@RequestParam Long userId,
                                               @RequestParam(required = false) Integer status) {
        return R.ok(couponService.listUserCoupons(userId, status));
    }

    @PostMapping("/apply")
    public R<BigDecimal> apply(@Valid @RequestBody CouponApplyDTO dto) {
        return R.ok(couponService.applyCoupon(dto));
    }
}
