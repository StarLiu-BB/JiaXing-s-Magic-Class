package com.zhixue.ai.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.ai.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 知识库索引初始化：确保 vector 字段是维度正确的 dense_vector，并让索引内容与数据库一致。
 *
 * <ul>
 *   <li>索引不存在：创建并把数据库中的分片全部写入（种子知识此前从未写入 ES，永远检索不到）</li>
 *   <li>维度与当前向量模型不符（例如从 DashScope 的 1536 维换成本地 qwen3-embedding 的 1024 维）：
 *       ES 不允许修改 dims，只能删除重建，再从数据库全量写回</li>
 *   <li>维度一致：不重建，避免每次重启都清空</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeIndexInitializer {

    /** 与 VectorStoreServiceImpl 使用的索引名保持一致。 */
    public static final String INDEX = "zhixue_kb";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final KnowledgeBaseService knowledgeBaseService;

    /** 必须与 EmbeddingService 输出维度一致，否则写入会被 ES 拒绝。 */
    @Value("${ai.llm.embedding-dimension:1024}")
    private int dimension;

    @Value("${zhixue.integration.ai.mode:real}")
    private String aiMode;

    @EventListener(ApplicationReadyEvent.class)
    public void ensureIndex() {
        if ("stub".equalsIgnoreCase(aiMode)) {
            log.info("AI 处于 stub 模式，跳过 ES 知识库索引初始化");
            return;
        }
        try {
            int existing = currentDims();
            if (existing == dimension) {
                if (documentCount() == 0) {
                    log.info("知识库索引为空，从数据库写入分片 index={}", INDEX);
                    knowledgeBaseService.reindexAll();
                } else {
                    log.info("知识库索引已存在且维度一致，跳过 index={} dims={}", INDEX, dimension);
                }
                return;
            }
            if (existing > 0) {
                log.warn("知识库索引维度 {} 与当前向量模型 {} 不符，删除重建 index={}", existing, dimension, INDEX);
                restClient.performRequest(new Request("DELETE", "/" + INDEX));
            }
            createIndex();
            log.info("知识库索引创建完成 index={} dims={}", INDEX, dimension);
            knowledgeBaseService.reindexAll();
        } catch (Exception e) {
            // ES/模型属可选依赖：初始化失败只告警，不阻断服务启动；但语义检索会因此不可用
            log.error("知识库索引初始化失败，语义检索将不可用 index={}", INDEX, e);
        }
    }

    /** 当前索引中 vector 字段的维度；索引不存在时返回 -1，存在但无 dense_vector 映射时返回 0 */
    int currentDims() throws Exception {
        try {
            Response response = restClient.performRequest(new Request("GET", "/" + INDEX + "/_mapping"));
            JsonNode root = objectMapper.readTree(response.getEntity().getContent());
            return root.path(INDEX).path("mappings").path("properties").path("vector").path("dims").asInt(0);
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                return -1;
            }
            throw e;
        }
    }

    private long documentCount() throws Exception {
        Response response = restClient.performRequest(new Request("GET", "/" + INDEX + "/_count"));
        return objectMapper.readTree(response.getEntity().getContent()).path("count").asLong(0);
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
                        "documentId", Map.of("type", "long"),
                        "courseId", Map.of("type", "long"),
                        "content", Map.of("type", "text"),
                        "vector", vectorField)));

        Request request = new Request("PUT", "/" + INDEX);
        request.setJsonEntity(objectMapper.writeValueAsString(body));
        restClient.performRequest(request);
    }
}
