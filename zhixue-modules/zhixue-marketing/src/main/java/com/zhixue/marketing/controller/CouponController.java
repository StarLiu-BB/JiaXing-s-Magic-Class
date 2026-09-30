package com.zhixue.marketing.controller;

import com.zhixue.common.core.domain.PageQuery;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.security.annotation.RequireLogin;
import com.zhixue.common.security.annotation.RequirePermission;
import com.zhixue.common.security.utils.SecurityUtils;
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

    /** 运营查看优惠券列表与统计 */
    static final String PERM_LIST = "marketing:coupon:list";
    /** 运营新建/修改/删除/启停/发放：修复前学生可新建面额 999、门槛 0 的券 */
    static final String PERM_EDIT = "marketing:coupon:edit";

    private final CouponService couponService;

    @RequirePermission(PERM_EDIT)
    @PostMapping
    public R<Coupon> create(@Valid @RequestBody CouponCreateDTO dto) {
        return R.ok(couponService.create(dto));
    }

    @RequirePermission(PERM_EDIT)
    @PostMapping("/create")
    public R<Coupon> createAlias(@Valid @RequestBody CouponCreateDTO dto) {
        return R.ok(couponService.create(dto));
    }

    @RequirePermission(PERM_EDIT)
    @PutMapping
    public R<Coupon> update(@Valid @RequestBody CouponUpdateDTO dto) {
        return R.ok(couponService.update(dto));
    }

    @RequirePermission(PERM_LIST)
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

    @RequirePermission(PERM_EDIT)
    @DeleteMapping("/{couponIds}")
    public R<Void> delete(@PathVariable String couponIds) {
        List<Long> ids = Arrays.stream(couponIds.split(","))
                .filter(StringUtils::hasText)
                .map(Long::valueOf)
                .toList();
        couponService.deleteByIds(ids);
        return R.ok();
    }

    @RequirePermission(PERM_EDIT)
    @PutMapping("/{couponId}/status")
    public R<Void> changeStatus(@PathVariable Long couponId, @Valid @RequestBody CouponStatusDTO dto) {
        couponService.changeStatus(couponId, dto.getStatus());
        return R.ok();
    }

    @RequirePermission(PERM_EDIT)
    @PostMapping("/{couponId}/issue")
    public R<Integer> issue(@PathVariable Long couponId, @RequestBody(required = false) CouponIssueDTO dto) {
        List<Long> userIds = dto == null ? null : dto.getUserIds();
        return R.ok(couponService.issue(couponId, userIds));
    }

    @RequirePermission(PERM_LIST)
    @GetMapping("/{couponId}/statistics")
    public R<Map<String, Object>> statistics(@PathVariable Long couponId) {
        return R.ok(couponService.statistics(couponId));
    }

    @GetMapping("/available")
    public R<List<Coupon>> listAvailable() {
        return R.ok(couponService.listAvailable());
    }

    /** 为当前登录用户领券；请求体中的 userId 被忽略（修复前可替他人领券）。 */
    @RequireLogin
    @PostMapping("/claim")
    public R<CouponUser> claim(@Valid @RequestBody CouponClaimDTO dto) {
        dto.setUserId(SecurityUtils.requireUserId());
        return R.ok(couponService.claim(dto));
    }

    /** 运营（marketing:coupon:list）可查任意用户；其余用户只能查自己的券。 */
    @RequireLogin
    @GetMapping("/user/list")
    public R<List<CouponUser>> listUserCoupons(@RequestParam(required = false) Long userId,
                                               @RequestParam(required = false) Integer status) {
        Long scopedUserId = SecurityUtils.hasAnyPermission(PERM_LIST) && userId != null
                ? userId : SecurityUtils.requireUserId();
        return R.ok(couponService.listUserCoupons(scopedUserId, status));
    }

    /** 以当前登录用户核销计算；请求体中的 userId 被忽略。 */
    @RequireLogin
    @PostMapping("/apply")
    public R<BigDecimal> apply(@Valid @RequestBody CouponApplyDTO dto) {
        dto.setUserId(SecurityUtils.requireUserId());
        return R.ok(couponService.applyCoupon(dto));
    }
}
