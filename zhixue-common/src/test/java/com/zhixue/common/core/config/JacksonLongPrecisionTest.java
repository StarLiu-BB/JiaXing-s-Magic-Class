package com.zhixue.common.core.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Long 精度保护测试。
 *
 * <p>覆盖 D48：实体主键使用雪花 ID（{@code IdType.ASSIGN_ID}，19 位），
 * 超过 JavaScript 安全整数 2^53-1。实测：</p>
 * <pre>
 *   后端返回 2104269046074900481
 *   前端解析 2104269046074900500   ← 末尾精度丢失
 * </pre>
 * <p>前端再用这个 id 编辑/删除，后端返回"不存在"——
 * 即通过管理后台<b>新建的任何记录都无法再被编辑或删除</b>。
 * 种子数据用的是小 id，所以既有功能看起来一切正常，问题极其隐蔽。</p>
 */
class JacksonLongPrecisionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonLongPrecisionConfig.class, JacksonAutoConfiguration.class));

    /** 模拟一个同时含大 id 与普通数值的响应体。 */
    record Payload(Long id, long primitiveId, Long total, Long small, Long nullable, Long negativeBig) {
    }

    private static final long SNOWFLAKE = 2104269046074900481L;

    private JsonNode serialize(ObjectMapper mapper) throws Exception {
        Payload p = new Payload(SNOWFLAKE, SNOWFLAKE, 42L, 1003L, null, -SNOWFLAKE);
        return mapper.readTree(mapper.writeValueAsString(p));
    }

    @Test
    void unsafeLongMustBeSerializedAsString() {
        runner.run(ctx -> {
            JsonNode json = serialize(ctx.getBean(ObjectMapper.class));

            assertThat(json.get("id").isTextual())
                    .as("超出 JS 安全整数的 Long 必须输出为字符串，否则前端精度丢失")
                    .isTrue();
            assertThat(json.get("id").asText()).isEqualTo("2104269046074900481");
        });
    }

    @Test
    void primitiveUnsafeLongMustAlsoBeString() {
        runner.run(ctx -> {
            JsonNode json = serialize(ctx.getBean(ObjectMapper.class));
            assertThat(json.get("primitiveId").isTextual()).isTrue();
            assertThat(json.get("primitiveId").asText()).isEqualTo("2104269046074900481");
        });
    }

    @Test
    void safeLongMustStayNumberToAvoidBreakingExistingFrontend() {
        runner.run(ctx -> {
            JsonNode json = serialize(ctx.getBean(ObjectMapper.class));

            // total、计数、种子小 id 保持数字：前端分页等逻辑依赖数值类型，
            // 一刀切转字符串会破坏当前正常工作的代码
            assertThat(json.get("total").isNumber()).as("total 应保持数字").isTrue();
            assertThat(json.get("total").asLong()).isEqualTo(42L);
            assertThat(json.get("small").isNumber()).as("小 id 应保持数字").isTrue();
        });
    }

    @Test
    void negativeUnsafeLongMustBeString() {
        runner.run(ctx -> {
            JsonNode json = serialize(ctx.getBean(ObjectMapper.class));
            assertThat(json.get("negativeBig").isTextual()).isTrue();
        });
    }

    @Test
    void nullMustStayNull() {
        runner.run(ctx -> {
            JsonNode json = serialize(ctx.getBean(ObjectMapper.class));
            assertThat(json.get("nullable").isNull()).isTrue();
        });
    }

    @Test
    void stringIdFromFrontendMustDeserializeBackToExactLong() {
        runner.run(ctx -> {
            ObjectMapper mapper = ctx.getBean(ObjectMapper.class);
            // 前端拿到字符串 id 后原样回传，后端必须能精确还原
            record Req(Long id) {
            }
            Req req = mapper.readValue("{\"id\":\"2104269046074900481\"}", Req.class);
            assertThat(req.id()).isEqualTo(SNOWFLAKE);
        });
    }

    @Test
    void mustPreserveSpringBootDefaultsSuchAsJavaTime() {
        runner.run(ctx -> {
            ObjectMapper mapper = ctx.getBean(ObjectMapper.class);
            // 必须是在 Spring Boot 默认 ObjectMapper 上定制，而非替换它，
            // 否则会丢失 JavaTimeModule，导致 LocalDateTime 无法序列化（D3 的根因）
            String json = mapper.writeValueAsString(java.time.LocalDateTime.of(2026, 9, 28, 10, 0));
            assertThat(json).contains("2026-09-28");
        });
    }

    @Test
    void mustBeRegisteredAsAutoConfigurationForAllServices() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertThat(in).isNotNull();
            String imports = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(imports)
                    .as("必须注册为自动装配，才能对全部 9 个服务生效")
                    .contains(JacksonLongPrecisionConfig.class.getName());
        }
    }
}
