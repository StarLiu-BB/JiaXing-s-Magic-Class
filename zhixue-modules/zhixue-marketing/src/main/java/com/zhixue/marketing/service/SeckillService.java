package com.zhixue.marketing.service;

import com.zhixue.marketing.domain.dto.SeckillRequestDTO;
import com.zhixue.marketing.domain.entity.SeckillActivity;

import java.util.List;

/**
 * 秒杀服务接口
 * 作用：定义秒杀活动相关的业务功能规范，包括库存预热、执行秒杀和查询在线活动等。
 * 所有秒杀相关的业务逻辑都要通过这个接口来调用，确保代码的规范性和可扩展性。
 */
public interface SeckillService {

    /**
     * 预热秒杀活动库存
     * 作用：将指定秒杀活动的库存提前加载到Redis中，提高秒杀时的性能。
     * @param activityId 秒杀活动ID
     */
    void preloadStock(Long activityId);

    /**
     * 执行秒杀操作
     * 作用：处理用户的秒杀请求，检查秒杀条件，执行库存扣减，并生成秒杀订单。
     * 使用Lua脚本保证Redis操作的原子性，防止库存超卖。
     * @param dto 秒杀请求参数（包含活动ID、用户ID等信息）
     * @return 生成的秒杀订单号
     */
    String seckill(SeckillRequestDTO dto);

    /**
     * 查询在线的秒杀活动列表
     * 作用：获取当前正在进行中的所有秒杀活动，供用户查看和参与。
     * @return 在线秒杀活动列表
     */
    List<SeckillActivity> listOnline();
}


