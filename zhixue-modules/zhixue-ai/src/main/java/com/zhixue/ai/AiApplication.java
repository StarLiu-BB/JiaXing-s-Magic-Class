package com.zhixue.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * AI 智脑中心的启动类。
 * 这个类是整个 AI 服务模块的入口，负责启动 Spring Boot 应用程序。
 * 它提供智能问答、知识库管理、内容审核等功能。
 */
@SpringBootApplication
@EnableDiscoveryClient
public class AiApplication {

    /**
     * 程序的主入口方法。
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AiApplication.class, args);
    }
}


