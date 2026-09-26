package com.zhixue.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 交易中心的启动类。
 * 这个类是整个订单服务模块的入口，负责启动 Spring Boot 应用程序。
 */
// 必须显式扫描 com.zhixue.common，否则 GlobalExceptionHandler 不生效，
// ServiceException（如支付回调验签失败）会以 500 而非 4xx 返回。
@SpringBootApplication(scanBasePackages = {"com.zhixue.order", "com.zhixue.common"})
@EnableDiscoveryClient
public class OrderApplication {

    /**
     * 程序的主入口方法。
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}


