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
 * Feign fallback 工厂扫描测试。
 *
 * <p>开启断路器后，{@code fallbackFactory} 指定的类必须是容器中的 Bean，
 * 否则启动即失败：
 * {@code No fallbackFactory instance of type ... found for feign client}。</p>
 *
 * <p>易错点：{@code @EnableFeignClients(basePackages="com.zhixue.api")}
 * 只扫描 Feign 接口，不会注册该包下的 {@code @Component}。
 * 因此 scanBasePackages 必须同时包含 com.zhixue.api。</p>
 */
class FeignFallbackScanTest {

    private static final List<String> FEIGN_CONSUMER_APPS = Arrays.asList(
            "zhixue-auth/src/main/java/com/zhixue/auth/AuthApplication.java",
            "zhixue-modules/zhixue-interaction/src/main/java/com/zhixue/interaction/InteractionApplication.java");

    private Path repoRoot() {
        return Paths.get("").toAbsolutePath().getParent();
    }

    @Test
    void feignConsumersMustScanApiPackageForFallbackBeans() throws Exception {
        for (String app : FEIGN_CONSUMER_APPS) {
            Path path = repoRoot().resolve(app);
            assertThat(Files.exists(path)).as("启动类应存在: %s", path).isTrue();

            String content = Files.readString(path, StandardCharsets.UTF_8);
            // 必须出现在 scanBasePackages/@ComponentScan 中；
            // 仅出现在 @EnableFeignClients(basePackages=...) 是不够的——
            // 后者只扫 Feign 接口，不注册该包下的 @Component
            String scanDeclaration = extractScanDeclaration(content);
            assertThat(scanDeclaration)
                    .as("%s 启用了 Feign 断路器，但组件扫描未包含 com.zhixue.api，"
                            + "fallbackFactory 不是 Bean，启动会直接失败", app)
                    .contains("com.zhixue.api");
        }
    }

    /** 抽取 scanBasePackages / @ComponentScan 的声明内容，排除 @EnableFeignClients。 */
    private String extractScanDeclaration(String content) {
        StringBuilder sb = new StringBuilder();
        for (String raw : content.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("@EnableFeignClients")) {
                continue;
            }
            if (line.contains("scanBasePackages") || line.startsWith("@ComponentScan")
                    || line.contains("basePackages = {")) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    @Test
    void allFallbackFactoriesMustBeSpringComponents() throws Exception {
        List<String> factories = Arrays.asList(
                "zhixue-api/src/main/java/com/zhixue/api/system/factory/RemoteUserFallbackFactory.java",
                "zhixue-api/src/main/java/com/zhixue/api/course/factory/RemoteCourseFallbackFactory.java",
                "zhixue-api/src/main/java/com/zhixue/api/media/factory/RemoteMediaFallbackFactory.java",
                "zhixue-api/src/main/java/com/zhixue/api/ai/factory/RemoteAiFallbackFactory.java");

        for (String factory : factories) {
            String content = Files.readString(
                    repoRoot().resolve(factory), StandardCharsets.UTF_8);
            assertThat(content)
                    .as("%s 必须标注 @Component 才能被解析为 fallback Bean", factory)
                    .contains("@Component");
        }
    }
}
