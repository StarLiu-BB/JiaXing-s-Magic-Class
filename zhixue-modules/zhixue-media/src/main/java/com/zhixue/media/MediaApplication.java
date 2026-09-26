package com.zhixue.media;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 媒资中心启动类。
 */
// 必须显式扫描 com.zhixue.common：
// 否则 SecurityAccessAspect 与 GlobalExceptionHandler 都不加载，
// 权限注解形同装饰、ServiceException 会以 500 返回。
@SpringBootApplication(scanBasePackages = {"com.zhixue.media", "com.zhixue.common"})
@EnableDiscoveryClient
public class MediaApplication {

    public static void main(String[] args) {
        SpringApplication.run(MediaApplication.class, args);
    }
}

