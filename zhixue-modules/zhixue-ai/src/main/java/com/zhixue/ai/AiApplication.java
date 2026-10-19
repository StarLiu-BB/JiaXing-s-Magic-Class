package com.zhixue.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * AI 服务启动类：课程答疑、知识库、弹幕审核。模型由本机 Ollama 提供。
 *
 * <p>必须扫描 com.zhixue.common：否则鉴权切面（@RequireLogin/@RequirePermission）、
 * 内部请求校验（InternalAccessFilter）与全局异常处理全部不生效——修复前即是如此。</p>
 */
@SpringBootApplication(scanBasePackages = {"com.zhixue.ai", "com.zhixue.common"})
@EnableDiscoveryClient
public class AiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiApplication.class, args);
    }
}
