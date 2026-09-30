package com.zhixue.order;

import com.zhixue.api.course.RemoteCourseService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 交易中心的启动类。
 * 这个类是整个订单服务模块的入口，负责启动 Spring Boot 应用程序。
 */
// 必须显式扫描 com.zhixue.common，否则 GlobalExceptionHandler 不生效，
// ServiceException（如支付回调验签失败）会以 500 而非 4xx 返回。
@SpringBootApplication(scanBasePackages = {"com.zhixue.order", "com.zhixue.common"})
@EnableDiscoveryClient
// 仅注册下单核价所需的课程客户端。调用失败由 OrderServiceImpl 捕获并拒绝下单（fail-closed），
// 不依赖熔断 fallback，因此无需扫描 com.zhixue.api 的 fallbackFactory 组件。
@EnableFeignClients(clients = RemoteCourseService.class)
public class OrderApplication {

    /**
     * 程序的主入口方法。
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}


