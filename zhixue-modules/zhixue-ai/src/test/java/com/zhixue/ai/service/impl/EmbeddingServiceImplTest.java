package com.zhixue.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhixue.ai.config.LlmConfig;
import com.zhixue.ai.support.FakeOllama;
import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Embedding 服务测试。
 *
 * <p>覆盖 D34：原实现用 SHA-256 哈希伪造 128 维向量，kNN 检索结果等同随机。本测试锁定两个不变量：
 * 不得用哈希伪造向量；真实模式下取不到向量时必须显式失败。</p>
 *
 * <p>现改为本地 Ollama 的 /api/embed（qwen3-embedding:0.6b，1024 维），支持批量。</p>
 */
class EmbeddingServiceImplTest {

    private FakeOllama ollama;

    @BeforeEach
    void setUp() throws Exception {
        ollama = new FakeOllama();
    }

    @AfterEach
    void tearDown() {
        ollama.close();
    }

    private EmbeddingServiceImpl newService(String mode, String baseUrl) {
        LlmConfig config = new LlmConfig();
        config.setBaseUrl(baseUrl);
        config.setEmbeddingModel("qwen3-embedding:0.6b");
        config.setEmbeddingDimension(1024);
        config.setEmbeddingTimeoutSeconds(5);
        EmbeddingServiceImpl service = new EmbeddingServiceImpl(config);
        ReflectionTestUtils.setField(service, "aiMode", mode);
        return service;
    }

    private EmbeddingServiceImpl newService(String mode) {
        return newService(mode, ollama.baseUrl());
    }

    @Test
    void shouldReturnEmptyForBlankText() {
        assertThat(newService("real").embed("   ")).isEmpty();
        assertThat(newService("real").embed(null)).isEmpty();
        assertThat(ollama.paths()).isEmpty();
    }

    @Test
    void embedsThroughLocalOllama() {
        ollama.embedReturns(1024);

        List<Float> vec = newService("real").embed("Java 面向对象");

        assertThat(vec).hasSize(1024);
        assertThat(ollama.paths()).containsExactly("/api/embed");
        JsonNode req = ollama.lastRequest();
        assertThat(req.path("model").asText()).isEqualTo("qwen3-embedding:0.6b");
        assertThat(req.path("input").get(0).asText()).isEqualTo("Java 面向对象");
    }

    @Test
    void batchKeepsInputOrderInOneRequest() {
        ollama.embedReturns(1024);

        List<List<Float>> vectors = newService("real").embedBatch(List.of("甲", "乙", "丙"));

        assertThat(vectors).hasSize(3);
        assertThat(vectors.get(0).get(0)).isEqualTo(1f);
        assertThat(vectors.get(2).get(0)).isEqualTo(3f);
        assertThat(ollama.paths()).as("批量应一次请求完成").hasSize(1);
        assertThat(ollama.lastRequest().path("input").size()).isEqualTo(3);
    }

    @Test
    void stubModeShouldReturnEmptyRatherThanFabricatedVector() {
        assertThat(newService("stub").embed("Java 面向对象")).as("stub 模式不得伪造向量").isEmpty();
        assertThat(ollama.paths()).isEmpty();
    }

    @Test
    void realModeMustFailOnDimensionMismatch() {
        // 维度不符写入 ES 会被拒绝；必须在这里就暴露，而不是静默降级
        ollama.embedReturns(768);
        assertThatThrownBy(() -> newService("real").embed("Java"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("AI 服务暂不可用，请稍后再试");
    }

    @Test
    void realModeMustFailWhenOllamaUnreachable() {
        // 界面实测：修复前提示原样带出 "Connection refused: host.docker.internal/192.168.65.254:11434"，
        // 把内部地址暴露给了学生；详情只写日志，对外统一提示
        assertThatThrownBy(() -> newService("real", "http://127.0.0.1:1").embed("Java"))
                .isInstanceOf(ServiceException.class)
                .hasMessage("AI 服务暂不可用，请稍后再试")
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void sandboxModeDegradesToEmptyWhenOllamaUnreachable() {
        assertThat(newService("sandbox", "http://127.0.0.1:1").embed("Java")).isEmpty();
    }

    @Test
    void mustNotUseHashBasedFabrication() throws Exception {
        String source = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/java/com/zhixue/ai/service/impl/EmbeddingServiceImpl.java"),
                java.nio.charset.StandardCharsets.UTF_8);
        assertThat(source)
                .as("不得再用 SHA-256/MD5 等哈希伪造向量：哈希无语义连续性")
                .doesNotContain("MessageDigest");
    }
}
