package com.zhixue.ai.service.impl;

import com.zhixue.ai.config.LlmConfig;
import com.zhixue.common.core.exception.ServiceException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Embedding 服务测试。
 *
 * <p>覆盖 D34：原实现用 SHA-256 哈希伪造 128 维向量。
 * 实测语义区分度仅 0.029（真实向量为 0.857），kNN 检索结果等同随机，
 * 但对外仍宣称是"向量检索"。</p>
 *
 * <p>本测试锁定两个不变量：</p>
 * <ul>
 *   <li>不得再用哈希伪造向量（哈希无语义连续性）</li>
 *   <li>真实模式下无法取得向量时必须显式失败，
 *       不得退回伪造向量假装成功</li>
 * </ul>
 */
class EmbeddingServiceImplTest {

    private EmbeddingServiceImpl newService(String mode, String apiKey) {
        LlmConfig config = new LlmConfig();
        config.setApiKey(apiKey);
        EmbeddingServiceImpl service = new EmbeddingServiceImpl(config);
        ReflectionTestUtils.setField(service, "aiMode", mode);
        ReflectionTestUtils.setField(service, "embeddingModel", "text-embedding-v2");
        ReflectionTestUtils.setField(service, "dimension", 1536);
        return service;
    }

    @Test
    void shouldReturnEmptyForBlankText() {
        assertThat(newService("stub", "").embed("   ")).isEmpty();
        assertThat(newService("stub", "").embed(null)).isEmpty();
    }

    @Test
    void stubModeShouldReturnEmptyRatherThanFabricatedVector() {
        // stub 模式返回空向量，由上层走明确标注的词法降级；
        // 绝不能返回"看起来像向量"的哈希值，那会让调用方以为语义检索生效
        List<Float> vec = newService("stub", "").embed("Java 面向对象");

        assertThat(vec)
                .as("stub 模式不得伪造向量")
                .isEmpty();
    }

    @Test
    void realModeMustFailFastWhenApiKeyMissing() {
        assertThatThrownBy(() -> newService("real", "").embed("Java 面向对象"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("API Key");
    }

    @Test
    void sandboxModeShouldReturnEmptyWhenApiKeyMissing() {
        // sandbox 无 key 时返回空而非抛错，便于本地无凭据联调；
        // 同样不得伪造向量
        assertThat(newService("sandbox", "").embed("Java 面向对象")).isEmpty();
    }

    @Test
    void mustNotUseHashBasedFabrication() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/zhixue/ai/service/impl/EmbeddingServiceImpl.java"),
                java.nio.charset.StandardCharsets.UTF_8);

        assertThat(source)
                .as("不得再用 SHA-256/MD5 等哈希伪造向量：哈希无语义连续性，"
                        + "相近与无关文本的相似度几乎相同")
                .doesNotContain("MessageDigest");
    }

    @Test
    void mustDeclareVectorDimensionMatchingIndexMapping() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/zhixue/ai/service/impl/EmbeddingServiceImpl.java"),
                java.nio.charset.StandardCharsets.UTF_8);

        // 维度必须可配置且与 ES dense_vector 的 dims 一致，
        // 不一致会导致写入被 ES 拒绝
        assertThat(source).contains("embedding.dimension");
    }
}
