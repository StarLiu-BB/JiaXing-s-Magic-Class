package com.zhixue.marketing.config;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * XXL-Job 执行器装配测试。
 *
 * <p>覆盖 D20：项目声明了两个 {@code @XxlJob} 处理器（秒杀库存预热、
 * 优惠券过期），也配置了 admin 地址，但全仓库没有任何
 * {@code XxlJobSpringExecutor} Bean —— 执行器从不向调度中心注册，
 * 这两个任务永远不会被触发。</p>
 */
class XxlJobWiringTest {

    private Path repoRoot() {
        return Paths.get("").toAbsolutePath().getParent().getParent();
    }

    private Path marketingMain() {
        return repoRoot().resolve("zhixue-modules/zhixue-marketing/src/main/java/com/zhixue/marketing");
    }

    @Test
    void mustProvideXxlJobSpringExecutorBean() throws Exception {
        Path config = marketingMain().resolve("config/XxlJobConfig.java");

        assertThat(Files.exists(config))
                .as("缺少 XxlJobConfig，执行器不会注册到调度中心，@XxlJob 任务永不执行")
                .isTrue();

        String content = Files.readString(config, StandardCharsets.UTF_8);
        assertThat(content).contains("XxlJobSpringExecutor");
        assertThat(content).as("必须声明为 Bean 才会被 Spring 管理").contains("@Bean");
    }

    @Test
    void executorMustReadConfigurationFromProperties() throws Exception {
        String content = Files.readString(
                marketingMain().resolve("config/XxlJobConfig.java"), StandardCharsets.UTF_8);

        // 地址与 appname 必须来自配置，便于各环境切换
        assertThat(content).contains("xxl.job.admin.addresses");
        assertThat(content).contains("xxl.job.executor.appname");
    }

    @Test
    void jobHandlersMustExist() throws Exception {
        Path tasks = marketingMain().resolve("task");
        String preload = Files.readString(
                tasks.resolve("SeckillPreloadTask.java"), StandardCharsets.UTF_8);
        String expire = Files.readString(
                tasks.resolve("CouponExpireTask.java"), StandardCharsets.UTF_8);

        assertThat(preload).contains("@XxlJob(\"seckillPreloadHandler\")");
        assertThat(expire).contains("@XxlJob(\"couponExpireHandler\")");
    }
}
