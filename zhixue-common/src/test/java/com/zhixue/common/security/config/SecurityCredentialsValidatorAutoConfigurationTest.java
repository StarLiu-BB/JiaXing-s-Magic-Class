package com.zhixue.common.security.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证凭据校验器确实被 Spring 自动装配接管，
 * 而不是一个"写了但没人调用"的死类。
 */
class SecurityCredentialsValidatorAutoConfigurationTest {

    private static final String IMPORTS_RESOURCE =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SecurityCredentialsValidator.class));

    @Test
    void shouldBeRegisteredAsAutoConfiguration() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(IMPORTS_RESOURCE)) {
            assertThat(in).as("自动装配清单必须存在").isNotNull();
            String imports = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(imports).contains(SecurityCredentialsValidator.class.getName());
        }
    }

    @Test
    void contextShouldFailToStartWithLeakedJwtSecret() {
        runner.withPropertyValues(
                        "zhixue.security.jwt-secret="
                                + "emhpeHVlY2xvdWRqd3RzZWNyZXRrZXkyMDI0bXVzdGJlYXRsZWFzdDI1NmJpdHNsb25n")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasRootCauseInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("已泄露"));
    }

    @Test
    void contextShouldFailToStartWithWeakJwtSecret() {
        runner.withPropertyValues("zhixue.security.jwt-secret=weak")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasRootCauseInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("256"));
    }

    @Test
    void contextShouldStartWhenCredentialsAreStrong() {
        runner.withPropertyValues(
                        "zhixue.security.jwt-secret=Zy9Xq2LpR7TmVn4KdJw8BsEuHc3FgA6YtNxPzQrWvMk=",
                        "zhixue.security.internal-token=b7f4c1a9e2d84f3ba6c50d7e19f83a2c")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void contextShouldStartForServicesThatDoNotDeclareCredentials() {
        // media / order / marketing / ai 不声明这两项配置，必须不受影响
        runner.run(context -> assertThat(context).hasNotFailed());
    }
}
