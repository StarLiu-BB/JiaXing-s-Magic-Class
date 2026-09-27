package com.zhixue.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 知识库索引初始化：确保向量字段被正确映射为 dense_vector。
 *
 * <p>此前项目没有任何索引定义，直接 {@code POST /zhixue_kb/_doc} 写入。
 * ES 会把 vector 动态映射成普通 {@code float[]}，
 * 后续 {@code knn} 查询报错并被 catch 后静默降级为词法匹配 ——
 * "向量检索"从未真正执行。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeIndexInitializer {

    /** 与 VectorStoreServiceImpl 使用的索引名保持一致。 */
    public static final String INDEX = "zhixue_kb";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    /** 必须与 EmbeddingService 输出维度一致，否则写入会被 ES 拒绝。 */
    @Value("${ai.dashscope.embedding.dimension:1536}")
    private int dimension;

    @Value("${zhixue.integration.ai.mode:sandbox}")
    private String aiMode;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndex() {
        if ("stub".equalsIgnoreCase(aiMode)) {
            log.info("AI 处于 stub 模式，跳过 ES 知识库索引初始化");
            return;
        }
        try {
            if (indexExists()) {
                log.info("知识库索引已存在，跳过创建 index={}", INDEX);
                return;
            }
            createIndex();
            log.info("知识库索引创建完成 index={}, dims={}", INDEX, dimension);
        } catch (Exception e) {
            // ES 属可选依赖：初始化失败只告警，不阻断服务启动。
            // 但语义检索会因此不可用，日志需明确说明后果。
            log.error("知识库索引初始化失败，语义检索将不可用 index={}", INDEX, e);
        }
    }

    private boolean indexExists() throws Exception {
        Response response = restClient.performRequest(new Request("HEAD", "/" + INDEX));
        return response.getStatusLine().getStatusCode() == 200;
    }

    private void createIndex() throws Exception {
        Map<String, Object> vectorField = Map.of(
                "type", "dense_vector",
                "dims", dimension,
                "index", true,
                // 文本向量检索的常规选择，显式声明避免依赖默认值
                "similarity", "cosine");

        Map<String, Object> body = Map.of(
                "mappings", Map.of("properties", Map.of(
                        "chunkId", Map.of("type", "long"),
                        "content", Map.of("type", "text"),
                        "vector", vectorField)));

        Request request = new Request("PUT", "/" + INDEX);
        request.setJsonEntity(objectMapper.writeValueAsString(body));
        restClient.performRequest(request);
    }
}
