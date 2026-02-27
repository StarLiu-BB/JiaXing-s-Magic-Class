package com.zhixue.marketing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 营销中心启动类
 * 作用：这是营销模块的入口，负责启动营销相关的所有功能。
 * 包括优惠券发放、秒杀活动等营销功能都从这里开始运行。
 */
@SpringBootApplication
@EnableDiscoveryClient
public class MarketingApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketingApplication.class, args);
        System.out.println("营销中心启动成功");
    }
}


