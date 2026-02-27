package com.zhixue.system;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * <p>
 * 系统服务启动类。
 * </p>
 */
@SpringBootApplication(scanBasePackages = {"com.zhixue.system", "com.zhixue.common"})
@EnableDiscoveryClient
@MapperScan("com.zhixue.system.mapper")
public class SystemApplication {

    public static void main(String[] args) {
        // 设置Nacos系统属性，确保连接到正确的服务器
        System.setProperty("nacos.server-addr", "192.168.211.175:8848");
        System.setProperty("nacos.grpc.port", "9848");
        SpringApplication.run(SystemApplication.class, args);
    }
}