package com.zhixue.auth;

import com.zhixue.common.security.config.SecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 认证服务启动类
 * 作用：这是认证服务的入口，程序从这里开始运行。
 * 认证服务负责处理用户的登录、注册、退出登录等功能。
 * 用户登录成功后，会得到一个登录凭证，后续访问其他服务时需要带上这个凭证来证明身份。
 */
@SpringBootApplication(
        scanBasePackages = {"com.zhixue.auth", "com.zhixue.common"},
        exclude = {
                DataSourceAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class
        }
)
// 临时注释掉EnableDiscoveryClient以排除Nacos连接问题
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.zhixue.api")
@EnableConfigurationProperties(SecurityProperties.class)
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
        System.out.println("启动成功");
    }
}