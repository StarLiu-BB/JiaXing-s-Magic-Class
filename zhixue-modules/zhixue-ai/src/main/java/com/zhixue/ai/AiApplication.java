package com.zhixue.ai;

import com.zhixue.api.course.RemoteCourseOutlineService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * AI 服务启动类：课程答疑、知识库、弹幕审核。模型由本机 Ollama 提供。
 *
 * <p>必须扫描 com.zhixue.common：否则鉴权切面（@RequireLogin/@RequirePermission）、
 * 内部请求校验（InternalAccessFilter）与全局异常处理全部不生效——修复前即是如此。</p>
 */
@SpringBootApplication(scanBasePackages = {"com.zhixue.ai", "com.zhixue.common"})
@EnableDiscoveryClient
// 只注册推荐卡片需要的课程大纲客户端；失败时推荐为空，不影响回答
@EnableFeignClients(clients = RemoteCourseOutlineService.class)
public class AiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiApplication.class, args);
    }
}
