package com.zhixue.course;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 课程中心的启动类。
 * 这个类是整个课程服务模块的入口，负责启动 Spring Boot 应用程序。
 */
@SpringBootApplication
@EnableDiscoveryClient
@MapperScan("com.zhixue.course.mapper")
public class CourseApplication {

    /**
     * 程序的主入口方法。
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(CourseApplication.class, args);
        System.out.println("===course模块启动成功===");
    }
}

