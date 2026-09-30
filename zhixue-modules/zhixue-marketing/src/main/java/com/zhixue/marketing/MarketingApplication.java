package com.zhixue.marketing;

import com.zhixue.api.order.RemoteOrderService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 营销中心启动类
 * 作用：这是营销模块的入口，负责启动营销相关的所有功能。
 * 包括优惠券发放、秒杀活动等营销功能都从这里开始运行。
 */
// 必须扫描 com.zhixue.common：否则鉴权切面、InternalAccessFilter、GlobalExceptionHandler
// 都不生效 —— 修复前学生可新建秒杀活动、以他人身份秒杀，业务异常以 500 返回
@SpringBootApplication(scanBasePackages = {"com.zhixue.marketing", "com.zhixue.common"})
@EnableDiscoveryClient
// 仅注册秒杀成交后建单所需的订单客户端；失败由 SeckillServiceImpl 回滚库存并告知用户
@EnableFeignClients(clients = RemoteOrderService.class)
public class MarketingApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketingApplication.class, args);
        System.out.println("营销中心启动成功");
    }
}


