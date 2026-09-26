package com.zhixue.interaction.service;

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
 * 当前用户解析方式测试。
 *
 * <p>本项目的身份信息由网关校验 JWT 后注入 HTTP 头，
 * {@code SecurityUtils} 直接读取这些头；而 {@code SecurityContextHolder}
 * 是一个 ThreadLocal，全项目没有任何地方向它写入。</p>
 *
 * <p>因此用 SecurityContextHolder.getLoginUser() 取用户必然拿到 null，
 * 导致收藏、点赞等接口恒定返回"请先登录"，即使用户已登录。</p>
 */
class CurrentUserResolutionTest {

    private static final List<String> SERVICE_IMPLS = Arrays.asList(
            "zhixue-modules/zhixue-interaction/src/main/java/com/zhixue/interaction/service/impl/FavoriteServiceImpl.java",
            "zhixue-modules/zhixue-interaction/src/main/java/com/zhixue/interaction/service/impl/LikeServiceImpl.java");

    private Path repoRoot() {
        // 从 zhixue-modules/zhixue-interaction 回溯两级到仓库根
        return Paths.get("").toAbsolutePath().getParent().getParent();
    }

    @Test
    void mustNotRelyOnUnpopulatedThreadLocalForCurrentUser() throws IOException {
        for (String file : SERVICE_IMPLS) {
            Path path = repoRoot().resolve(file);
            assertThat(Files.exists(path)).as("源文件应存在: %s", path).isTrue();

            String content = Files.readString(path, StandardCharsets.UTF_8);
            assertThat(content)
                    .as("%s 使用了 SecurityContextHolder（ThreadLocal 从未被填充），"
                            + "会导致已登录用户也被判为未登录", file)
                    .doesNotContain("SecurityContextHolder.getLoginUser()");
        }
    }

    @Test
    void mustResolveCurrentUserFromGatewayInjectedHeaders() throws IOException {
        for (String file : SERVICE_IMPLS) {
            String content = Files.readString(
                    repoRoot().resolve(file), StandardCharsets.UTF_8);
            assertThat(content)
                    .as("%s 应改用 SecurityUtils.getUserId()（读取网关注入的可信身份头）", file)
                    .contains("SecurityUtils.getUserId()");
        }
    }
}
