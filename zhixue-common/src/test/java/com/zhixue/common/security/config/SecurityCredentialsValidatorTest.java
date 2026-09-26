package com.zhixue.common.security.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 启动期安全凭据校验测试。
 *
 * <p>覆盖阶段 0 的验收标准：弱密钥、空密钥、以及已泄露到公开仓库的默认值
 * 必须在应用启动阶段直接失败，而不是静默降级。</p>
 */
class SecurityCredentialsValidatorTest {

    /** 已提交进 Git 历史、必须永久拒绝的 JWT 密钥（Base64 形式）。 */
    private static final String LEAKED_JWT_SECRET_BASE64 =
            "emhpeHVlY2xvdWRqd3RzZWNyZXRrZXkyMDI0bXVzdGJlYXRsZWFzdDI1NmJpdHNsb25n";

    /** 上述密钥解码后的明文形式。 */
    private static final String LEAKED_JWT_SECRET_PLAIN =
            "zhixuecloudjwtsecretkey2024mustbeatleast256bitslong";

    /** 已提交进 Git 历史、必须永久拒绝的内部调用令牌。 */
    private static final String LEAKED_INTERNAL_TOKEN =
            "local-dev-internal-token-change-before-prod";

    private static final String STRONG_JWT_SECRET =
            "Zy9Xq2LpR7TmVn4KdJw8BsEuHc3FgA6YtNxPzQrWvMk=";

    private static final String STRONG_INTERNAL_TOKEN = "b7f4c1a9e2d84f3ba6c50d7e19f83a2c";

    private static void validate(MockEnvironment environment) {
        new SecurityCredentialsValidator(environment).afterPropertiesSet();
    }

    @Test
    void shouldPassWhenCredentialsNotDeclared() {
        // media / order / marketing / ai 未声明这两项配置，不应被误伤
        assertThatCode(() -> validate(new MockEnvironment())).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectBlankJwtSecret() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("zhixue.security.jwt-secret", "   ");

        assertThatThrownBy(() -> validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ZHIXUE_JWT_SECRET");
    }

    @Test
    void shouldRejectJwtSecretWeakerThan256Bits() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("zhixue.security.jwt-secret", "too-short-secret");

        assertThatThrownBy(() -> validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256");
    }

    @Test
    void shouldRejectLeakedJwtSecretInBase64Form() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("zhixue.security.jwt-secret", LEAKED_JWT_SECRET_BASE64);

        assertThatThrownBy(() -> validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已泄露");
    }

    @Test
    void shouldRejectLeakedJwtSecretInPlainForm() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("zhixue.security.jwt-secret", LEAKED_JWT_SECRET_PLAIN);

        assertThatThrownBy(() -> validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已泄露");
    }

    @Test
    void shouldRejectUnsubstitutedPlaceholder() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("zhixue.security.jwt-secret", "__GENERATE__");

        assertThatThrownBy(() -> validate(environment))
                .isInstanceOf(IllegalStateException.class);
    }

    /**
     * 锁住 Spring 自身的 fail-fast 行为：yml 中写 ${ZHIXUE_JWT_SECRET} 且无默认值时，
     * 环境变量缺失会在属性解析阶段直接抛错，无需校验器额外兜底。
     * 这正是移除硬编码兜底值后能拦住"配置漏透传"的机制，必须防止被回退。
     */
    @Test
    void springShouldFailFastWhenEnvVarIsNotInjected() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("zhixue.security.jwt-secret", "${ZHIXUE_JWT_SECRET}");

        assertThatThrownBy(() -> validate(environment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Could not resolve placeholder")
                .hasMessageContaining("ZHIXUE_JWT_SECRET");
    }

    @Test
    void shouldAcceptStrongJwtSecret() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("zhixue.security.jwt-secret", STRONG_JWT_SECRET);

        assertThatCode(() -> validate(environment)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectLeakedInternalToken() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("zhixue.security.jwt-secret", STRONG_JWT_SECRET)
                .withProperty("zhixue.security.internal-token", LEAKED_INTERNAL_TOKEN);

        assertThatThrownBy(() -> validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已泄露");
    }

    @Test
    void shouldRejectShortInternalToken() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("zhixue.security.internal-token", "short-token");

        assertThatThrownBy(() -> validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ZHIXUE_INTERNAL_TOKEN");
    }

    @Test
    void shouldAcceptStrongInternalToken() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("zhixue.security.jwt-secret", STRONG_JWT_SECRET)
                .withProperty("zhixue.security.internal-token", STRONG_INTERNAL_TOKEN);

        assertThatCode(() -> validate(environment)).doesNotThrowAnyException();
    }

    @Test
    void errorMessageShouldTellOperatorHowToFix() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("zhixue.security.jwt-secret", LEAKED_JWT_SECRET_BASE64);

        assertThatThrownBy(() -> validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(ex -> assertThat(ex.getMessage()).contains("openssl rand -base64 48"));
    }
}
