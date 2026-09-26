package com.zhixue.gateway.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网关以 {@code lb://服务名} 方式路由，被路由的服务必须能注册到 Nacos，
 * 否则请求会以 503 "Unable to find instance" 失败。
 *
 * <p>本测试锁定各业务模块都声明了 nacos-discovery 依赖。
 * 缺失时服务能独立启动、actuator 健康检查也通过，
 * 但经网关访问必然失败——这种"看起来健康其实不可达"的状态极难排查。</p>
 */
class RoutedServiceRegistrationTest {

    /** 服务名 -> 模块 pom 相对路径 */
    private static final Map<String, String> MODULE_POMS = new LinkedHashMap<>();

    static {
        MODULE_POMS.put("zhixue-auth", "zhixue-auth/pom.xml");
        MODULE_POMS.put("zhixue-system", "zhixue-modules/zhixue-system/pom.xml");
        MODULE_POMS.put("zhixue-course", "zhixue-modules/zhixue-course/pom.xml");
        MODULE_POMS.put("zhixue-media", "zhixue-modules/zhixue-media/pom.xml");
        MODULE_POMS.put("zhixue-order", "zhixue-modules/zhixue-order/pom.xml");
        MODULE_POMS.put("zhixue-marketing", "zhixue-modules/zhixue-marketing/pom.xml");
        MODULE_POMS.put("zhixue-interaction", "zhixue-modules/zhixue-interaction/pom.xml");
        MODULE_POMS.put("zhixue-ai", "zhixue-modules/zhixue-ai/pom.xml");
    }

    /** 从 gateway 模块目录回溯到仓库根目录。 */
    private Path repoRoot() {
        return Paths.get("").toAbsolutePath().getParent();
    }

    @Test
    void everyRoutedServiceMustDeclareNacosDiscovery() throws IOException {
        for (Map.Entry<String, String> entry : MODULE_POMS.entrySet()) {
            Path pom = repoRoot().resolve(entry.getValue());
            assertThat(Files.exists(pom))
                    .as("模块 pom 必须存在: %s", pom)
                    .isTrue();

            String content = Files.readString(pom, StandardCharsets.UTF_8);
            assertThat(content)
                    .as("%s 缺少 nacos-discovery 依赖，将无法注册到 Nacos，"
                            + "网关 lb:// 路由会返回 503（服务本身却显示健康）", entry.getKey())
                    .contains("spring-cloud-starter-alibaba-nacos-discovery");
        }
    }

    @Test
    void everyRoutedServiceMustDeclareBootstrapStarter() throws IOException {
        // 没有 bootstrap starter 时 bootstrap.yml 不会被加载，
        // 其中的 nacos 地址等配置全部失效
        for (Map.Entry<String, String> entry : MODULE_POMS.entrySet()) {
            String content = Files.readString(
                    repoRoot().resolve(entry.getValue()), StandardCharsets.UTF_8);
            assertThat(content)
                    .as("%s 缺少 spring-cloud-starter-bootstrap，bootstrap.yml 不会生效", entry.getKey())
                    .contains("spring-cloud-starter-bootstrap");
        }
    }
}
