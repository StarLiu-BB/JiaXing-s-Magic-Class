package com.zhixue.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhixue.ai.config.LlmConfig;
import com.zhixue.ai.service.EmbeddingService;
import com.zhixue.common.core.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文本向量化服务，调用阿里云百炼（DashScope）text-embedding 接口。
 *
 * <p>此前的实现用 SHA-256 哈希构造 128 维"向量"。哈希不具备语义连续性：
 * 实测语义相近文本相似度 0.8393、完全无关文本 0.8103，区分度仅 0.029，
 * 而真实向量为 0.857 —— 即 kNN 检索结果等同随机，
 * 却对外宣称是"向量检索"。</p>
 *
 * <p>设计约束：取不到真实向量时返回空列表或抛错，
 * <b>绝不返回伪造向量</b>。上层据此走明确标注的词法降级，
 * 而不是误以为语义检索已生效。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingServiceImpl implements EmbeddingService {

    private static final String ENDPOINT =
            "https://dashscope.aliyuncs.com/api/v1/services/embeddings/text-embedding/text-embedding";

    private final LlmConfig config;

    @Value("${zhixue.integration.ai.mode:sandbox}")
    private String aiMode;

    @Value("${ai.dashscope.embedding-model:text-embedding-v2}")
    private String embeddingModel;

    /** 必须与 ES dense_vector 的 dims 一致，否则写入会被 ES 拒绝。 */
    @Value("${ai.dashscope.embedding.dimension:1536}")
    private int dimension;

    @Value("${ai.dashscope.embedding-timeout-seconds:20}")
    private int timeoutSeconds;

    @Override
    public List<Float> embed(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        // stub 模式不访问外部服务，也不伪造向量
        if (isStubMode()) {
            return List.of();
        }
        if (!StringUtils.hasText(config.getApiKey())) {
            if (isRealMode()) {
                // 声称 real 模式却没有凭据属于配置错误，必须暴露
                throw new ServiceException(
                        "AI 运行于 real 模式但未配置 DashScope API Key，无法生成文本向量");
            }
            log.warn("未配置 DashScope API Key，跳过向量化（语义检索不可用）");
            return List.of();
        }

        try {
            JsonNode root = WebClient.builder()
                    .baseUrl(ENDPOINT)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.getApiKey())
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build()
                    .post()
                    .bodyValue(Map.of(
                            "model", embeddingModel,
                            "input", Map.of("texts", List.of(text))))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(timeoutSeconds));

            List<Float> vector = extractVector(root);
            if (vector.isEmpty()) {
                handleFailure("DashScope 返回的向量为空");
                return List.of();
            }
            if (vector.size() != dimension) {
                // 维度不符会导致 ES 写入失败，必须尽早暴露
                handleFailure(String.format(
                        "向量维度不符：期望 %d，实际 %d（请检查 ai.dashscope.embedding.dimension 与模型是否匹配）",
                        dimension, vector.size()));
                return List.of();
            }
            return vector;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 DashScope 向量化接口失败", e);
            handleFailure("向量化接口调用失败: " + e.getMessage());
            return List.of();
        }
    }

    private List<Float> extractVector(JsonNode root) {
        if (root == null) {
            return List.of();
        }
        JsonNode embeddings = root.path("output").path("embeddings");
        if (!embeddings.isArray() || embeddings.isEmpty()) {
            log.warn("DashScope 向量响应结构异常: {}", root.path("message").asText(""));
            return List.of();
        }
        JsonNode values = embeddings.get(0).path("embedding");
        List<Float> vector = new ArrayList<>(values.size());
        for (JsonNode value : values) {
            vector.add((float) value.asDouble());
        }
        return vector;
    }

    /** real 模式下向量化失败必须抛出，避免检索静默退化成词法匹配却无人知晓。 */
    private void handleFailure(String message) {
        if (isRealMode()) {
            throw new ServiceException(message);
        }
        log.warn("向量化降级：{}", message);
    }

    private boolean isStubMode() {
        return "stub".equalsIgnoreCase(aiMode);
    }

    private boolean isRealMode() {
        return "real".equalsIgnoreCase(aiMode);
    }
}
