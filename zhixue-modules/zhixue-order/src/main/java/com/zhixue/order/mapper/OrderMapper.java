package com.zhixue.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixue.order.domain.entity.Order;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单数据库操作接口。
 * 负责订单数据的增删改查操作，继承自数据库操作工具的基础接口。
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {
}


