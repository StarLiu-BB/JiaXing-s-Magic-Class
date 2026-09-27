package com.zhixue.marketing.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * XXL-Job 执行器配置。
 *
 * <p>没有这个 Bean 时，{@code @XxlJob} 注解的处理器不会注册到调度中心，
 * 任务永远不会被触发——而日志和配置看起来一切正常，极易误判为"已接入"。</p>
 *
 * <p>通过 {@code xxl.job.enabled} 控制：本地或测试环境没有 admin
 * 时可关闭，避免启动期反复重试注册刷满日志。</p>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "xxl.job.enabled", havingValue = "true", matchIfMissing = false)
public class XxlJobConfig {

    @Value("${xxl.job.admin.addresses:}")
    private String adminAddresses;

    @Value("${xxl.job.accessToken:}")
    private String accessToken;

    @Value("${xxl.job.executor.appname:zhixue-marketing-executor}")
    private String appName;

    @Value("${xxl.job.executor.address:}")
    private String address;

    @Value("${xxl.job.executor.ip:}")
    private String ip;

    @Value("${xxl.job.executor.port:9997}")
    private int port;

    @Value("${xxl.job.executor.logpath:logs/xxl-job}")
    private String logPath;

    @Value("${xxl.job.executor.logretentiondays:30}")
    private int logRetentionDays;

    @Bean
    public XxlJobSpringExecutor xxlJobSpringExecutor() {
        if (!StringUtils.hasText(adminAddresses)) {
            // 开启了 xxl.job.enabled 却没给地址属于配置错误，必须暴露而非静默降级
            throw new IllegalStateException(
                    "已启用 XXL-Job 但未配置 xxl.job.admin.addresses，请设置 ZHIXUE_XXL_JOB_ADDR");
        }
        XxlJobSpringExecutor executor = new XxlJobSpringExecutor();
        executor.setAdminAddresses(adminAddresses);
        executor.setAccessToken(accessToken);
        executor.setAppname(appName);
        executor.setAddress(address);
        executor.setIp(ip);
        executor.setPort(port);
        executor.setLogPath(logPath);
        executor.setLogRetentionDays(logRetentionDays);

        log.info("XXL-Job 执行器已装配 appname={}, admin={}, port={}", appName, adminAddresses, port);
        return executor;
    }
}
