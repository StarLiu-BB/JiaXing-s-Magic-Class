package com.zhixue.gateway;

import com.zhixue.common.mybatis.config.MybatisPlusConfig;
import com.zhixue.common.redis.config.RedisConfig;
import com.zhixue.common.security.config.SecurityProperties;
import com.zhixue.gateway.config.WhiteListConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 网关启动类
 * 作用：这是整个网关服务的入口，程序从这里开始运行。
 * 网关就像一个大门，所有的外部请求都要先经过这里，再转发到各个具体的服务。
 * 比如用户要访问用户服务、课程服务，请求都会先到网关，由网关决定怎么转发。
 */
@SpringBootApplication(
        scanBasePackages = {"com.zhixue.gateway", "com.zhixue.common.security"},
        exclude = {
                DataSourceAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class,
                MybatisPlusConfig.class,
                RedisConfig.class
        }
)
// 临时注释掉EnableDiscoveryClient以排除Nacos连接问题
@EnableDiscoveryClient
@EnableConfigurationProperties({WhiteListConfig.class, SecurityProperties.class})
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
        System.out.println("网关启动成功");
    }
}