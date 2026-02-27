package com.zhixue.marketing.controller;

import com.zhixue.common.core.domain.R;
import com.zhixue.marketing.domain.dto.CouponClaimDTO;
import com.zhixue.marketing.domain.dto.CouponCreateDTO;
import com.zhixue.marketing.domain.entity.Coupon;
import com.zhixue.marketing.domain.entity.CouponUser;
import com.zhixue.marketing.service.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 优惠券接口控制器
 * 作用：处理与优惠券相关的HTTP请求，比如创建优惠券、查询可用优惠券、领取优惠券等。
 * 它是营销模块对外提供优惠券功能的入口，前端通过调用这些接口来使用优惠券功能。
 */
@RestController
@RequestMapping("/coupon")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    /**
     * 创建优惠券
     * 作用：根据传入的参数创建一个新的优惠券，主要用于后台管理系统创建优惠券活动。
     * 请求方法：POST
     * 请求路径：/coupon/create
     */
    @PostMapping("/create")
    public R<Coupon> create(@Valid @RequestBody CouponCreateDTO dto) {
        return R.ok(couponService.create(dto));
    }

    /**
     * 查询可用的优惠券列表
     * 作用：找出所有当前时间可以使用的优惠券，供用户查看和领取。
     * 请求方法：GET
     * 请求路径：/coupon/available
     */
    @GetMapping("/available")
    public R<List<Coupon>> list() {
        return R.ok(couponService.listAvailable());
    }

    /**
     * 用户领取优惠券
     * 作用：处理用户领取优惠券的请求，为用户发放指定的优惠券。
     * 请求方法：POST
     * 请求路径：/coupon/claim
     */
    @PostMapping("/claim")
    public R<CouponUser> claim(@Valid @RequestBody CouponClaimDTO dto) {
        return R.ok(couponService.claim(dto));
    }
}


