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
        SpringApplication.run(SystemApplication.class, args);
    }
}
