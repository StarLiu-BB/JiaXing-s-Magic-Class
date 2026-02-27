package com.zhixue.marketing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixue.marketing.domain.entity.CouponUser;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户优惠券数据操作接口
 * 作用：用于操作数据库中的用户优惠券表，提供增删改查等基本功能。
 * 它基于数据库操作工具（MyBatis-Plus）实现，简化了数据库操作代码。
 */
@Mapper
public interface CouponUserMapper extends BaseMapper<CouponUser> {
}


