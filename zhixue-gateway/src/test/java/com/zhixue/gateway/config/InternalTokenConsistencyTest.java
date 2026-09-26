package com.zhixue.gateway.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内部令牌配置一致性测试。
 *
 * <p>鉴权模型：网关校验 JWT 后注入 {@code X-User-*} 头，并附带
 * {@code X-Internal-Token}；下游服务的 {@code InternalAccessFilter} 用本地
 * 配置的 internal-token 比对，只有匹配才认为这些身份头可信。</p>
 *
 * <p>因此：<b>凡是声明了 jwt-secret（即需要用户身份）的服务，必须同时声明
 * internal-token</b>。否则 isTrusted() 恒为 false，网关转发的合法请求会被
 * 当成伪造请求拒绝（返回"非法内部请求"），该服务的所有鉴权接口彻底不可用。</p>
 */
class InternalTokenConsistencyTest {

    private static final List<String> MODULE_YAMLS = Arrays.asList(
            "zhixue-auth/src/main/resources/application.yml",
            "zhixue-gateway/src/main/resources/application.yml",
            "zhixue-modules/zhixue-system/src/main/resources/application.yml",
            "zhixue-modules/zhixue-course/src/main/resources/application.yml",
            "zhixue-modules/zhixue-interaction/src/main/resources/application.yml");

    private Path repoRoot() {
        return Paths.get("").toAbsolutePath().getParent();
    }

    @Test
    void serviceDeclaringJwtSecretMustAlsoDeclareInternalToken() throws IOException {
        for (String yaml : MODULE_YAMLS) {
            Path path = repoRoot().resolve(yaml);
            assertThat(Files.exists(path)).as("配置文件应存在: %s", path).isTrue();

            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (!content.contains("jwt-secret")) {
                continue;
            }
            assertThat(content)
                    .as("%s 声明了 jwt-secret 却缺少 internal-token，"
                            + "InternalAccessFilter 会把网关注入的身份头判为伪造，"
                            + "该服务所有需要登录的接口都会返回『非法内部请求』", yaml)
                    .contains("internal-token");
        }
    }

    @Test
    void internalTokenMustNotHaveHardcodedDefault() throws IOException {
        for (String yaml : MODULE_YAMLS) {
            String content = Files.readString(repoRoot().resolve(yaml), StandardCharsets.UTF_8);
            if (!content.contains("internal-token")) {
                continue;
            }
            // 保持阶段 0 的成果：不得出现 ${VAR:默认值} 形式的兜底
            assertThat(content)
                    .as("%s 的 internal-token 不得带硬编码兜底值", yaml)
                    .contains("internal-token: ${ZHIXUE_INTERNAL_TOKEN}");
        }
    }
}
