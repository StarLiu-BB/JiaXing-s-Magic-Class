package com.zhixue.interaction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 互动中心启动类。
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.zhixue.api")
@EnableScheduling
// com.zhixue.api 必须纳入扫描：Feign fallbackFactory 是 @Component，
// @EnableFeignClients 只扫接口不注册组件，缺失会导致启动失败
@ComponentScan(basePackages = {"com.zhixue.interaction", "com.zhixue.common", "com.zhixue.api"})
public class InteractionApplication {

    public static void main(String[] args) {
        SpringApplication.run(InteractionApplication.class, args);
    }
}


