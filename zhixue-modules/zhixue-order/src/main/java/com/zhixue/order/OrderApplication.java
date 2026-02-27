package com.zhixue.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 交易中心的启动类。
 * 这个类是整个订单服务模块的入口，负责启动 Spring Boot 应用程序。
 */
@SpringBootApplication
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


