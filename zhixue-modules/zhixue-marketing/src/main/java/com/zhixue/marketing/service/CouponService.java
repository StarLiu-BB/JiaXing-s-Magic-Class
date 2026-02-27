package com.zhixue.marketing.service;

import com.zhixue.marketing.domain.dto.CouponClaimDTO;
import com.zhixue.marketing.domain.dto.CouponCreateDTO;
import com.zhixue.marketing.domain.entity.Coupon;
import com.zhixue.marketing.domain.entity.CouponUser;

import java.util.List;

/**
 * 优惠券服务接口
 * 作用：定义优惠券相关的业务功能规范，包括创建优惠券、查询可用优惠券和领取优惠券等。
 * 所有优惠券相关的业务逻辑都要通过这个接口来调用，确保代码的规范性和可扩展性。
 */
public interface CouponService {

    /**
     * 创建优惠券
     * 作用：根据传入的参数创建一个新的优惠券活动，包括优惠券的基本信息、使用规则等。
     * @param dto 优惠券创建参数（包含优惠券名称、类型、使用门槛等信息）
     * @return 创建成功的优惠券实体
     */
    Coupon create(CouponCreateDTO dto);

    /**
     * 查询可用优惠券列表
     * 作用：获取所有当前可用的优惠券，供用户查看和选择领取。
     * @return 可用优惠券列表
     */
    List<Coupon> listAvailable();

    /**
     * 用户领取优惠券
     * 作用：处理用户的优惠券领取请求，检查领取条件，为用户发放优惠券。
     * @param dto 优惠券领取参数（包含用户ID、优惠券ID等信息）
     * @return 用户领取的优惠券记录
     */
    CouponUser claim(CouponClaimDTO dto);
}


