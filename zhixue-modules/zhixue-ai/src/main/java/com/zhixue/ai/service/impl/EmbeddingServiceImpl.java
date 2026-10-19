package com.zhixue.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhixue.ai.config.LlmConfig;
import com.zhixue.ai.service.EmbeddingService;
import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文本向量化服务：调用本机 Ollama 的 /api/embed（默认 qwen3-embedding:0.6b，1024 维，中文效果好）。
 *
 * <p>D34：更早的实现用 SHA-256 哈希构造 128 维"向量"，语义区分度仅 0.029，kNN 检索等同随机。
 * 设计约束保持不变：取不到真实向量时返回空列表（sandbox）或抛错（real），
 * <b>绝不返回伪造向量</b>。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingServiceImpl implements EmbeddingService {

    private final LlmConfig config;

    @Value("${zhixue.integration.ai.mode:real}")
    private String aiMode;

    @Override
    public List<Float> embed(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        List<List<Float>> vectors = embedBatch(List.of(text));
        return vectors.isEmpty() ? List.of() : vectors.get(0);
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty() || texts.stream().anyMatch(t -> !StringUtils.hasText(t))) {
            return List.of();
        }
        // stub 模式不访问模型，也不伪造向量
        if (isStubMode()) {
            return List.of();
        }
        try {
            JsonNode root = WebClient.create(config.getBaseUrl())
                    .post()
                    .uri("/api/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "model", config.getEmbeddingModel(),
                            "input", texts,
                            "truncate", true,
                            "keep_alive", config.getKeepAlive()))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(Duration.ofSeconds(config.getEmbeddingTimeoutSeconds()));

            JsonNode embeddings = root == null ? null : root.path("embeddings");
            if (embeddings == null || !embeddings.isArray() || embeddings.size() != texts.size()) {
                return fail("向量化返回条数与输入不符", null);
            }
            List<List<Float>> result = new ArrayList<>(texts.size());
            for (JsonNode values : embeddings) {
                if (values.size() != config.getEmbeddingDimension()) {
                    // 维度不符写入 ES 会被拒绝，必须尽早暴露
                    return fail(String.format("向量维度不符：期望 %d，实际 %d（请检查 ai.llm.embedding-dimension 与模型 %s 是否匹配）",
                            config.getEmbeddingDimension(), values.size(), config.getEmbeddingModel()), null);
                }
                List<Float> vector = new ArrayList<>(values.size());
                for (JsonNode value : values) {
                    vector.add((float) value.asDouble());
                }
                result.add(vector);
            }
            return result;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            return fail("本地向量模型调用失败: " + e.getMessage(), e);
        }
    }

    /** real 模式下失败必须抛出，避免检索静默退化成词法匹配却无人知晓；详情只进日志，不把内部地址带给用户 */
    private List<List<Float>> fail(String message, Exception cause) {
        if (isRealMode()) {
            log.error("{}（{}）", message, config.getBaseUrl(), cause);
            throw new ServiceException(HttpStatus.SERVICE_UNAVAILABLE, "AI 服务暂不可用，请稍后再试", cause);
        }
        log.warn("向量化降级（语义检索不可用）：{}", message);
        return List.of();
    }

    private boolean isStubMode() {
        return "stub".equalsIgnoreCase(aiMode);
    }

    private boolean isRealMode() {
        return "real".equalsIgnoreCase(aiMode);
    }
}
