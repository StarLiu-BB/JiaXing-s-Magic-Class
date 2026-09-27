package com.zhixue.gateway.config;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feign 降级生效性测试。
 *
 * <p>覆盖 D22：4 个 Feign 客户端都声明了 {@code fallbackFactory}，
 * 但 Spring Cloud 2020+ 要求显式开启
 * {@code spring.cloud.openfeign.circuitbreaker.enabled=true}
 * 并引入断路器实现，否则 fallback 永远不会被调用 ——
 * 依赖服务故障时直接抛异常，级联故障无隔离。</p>
 */
class FeignCircuitBreakerTest {

    /** 会通过 Feign 调用其他服务的模块。 */
    private static final List<String> FEIGN_CONSUMERS = Arrays.asList(
            "zhixue-auth",
            "zhixue-modules/zhixue-interaction");

    private Path repoRoot() {
        return Paths.get("").toAbsolutePath().getParent();
    }

    @Test
    void feignConsumersMustEnableCircuitBreaker() throws Exception {
        for (String module : FEIGN_CONSUMERS) {
            Path yaml = repoRoot().resolve(module + "/src/main/resources/application.yml");
            assertThat(Files.exists(yaml)).as("配置应存在: %s", yaml).isTrue();

            String content = Files.readString(yaml, StandardCharsets.UTF_8);
            assertThat(content)
                    .as("%s 未开启 openfeign circuitbreaker，"
                            + "fallbackFactory 不会生效，降级形同虚设", module)
                    .contains("circuitbreaker");
            assertThat(content).contains("enabled: true");
        }
    }

    @Test
    void feignConsumersMustDeclareCircuitBreakerImplementation() throws Exception {
        for (String module : FEIGN_CONSUMERS) {
            String pom = Files.readString(
                    repoRoot().resolve(module + "/pom.xml"), StandardCharsets.UTF_8);
            assertThat(pom)
                    .as("%s 缺少断路器实现依赖，开启 circuitbreaker 也无法工作", module)
                    .contains("spring-cloud-starter-circuitbreaker-resilience4j");
        }
    }

    @Test
    void allFeignClientsMustDeclareFallbackFactory() throws Exception {
        List<String> clients = Arrays.asList(
                "zhixue-api/src/main/java/com/zhixue/api/system/RemoteUserService.java",
                "zhixue-api/src/main/java/com/zhixue/api/course/RemoteCourseService.java",
                "zhixue-api/src/main/java/com/zhixue/api/media/RemoteMediaService.java",
                "zhixue-api/src/main/java/com/zhixue/api/ai/RemoteAiService.java");

        for (String client : clients) {
            String content = Files.readString(
                    repoRoot().resolve(client), StandardCharsets.UTF_8);
            assertThat(content)
                    .as("%s 应声明 fallbackFactory 以便熔断时降级", client)
                    .contains("fallbackFactory");
        }
    }
}
