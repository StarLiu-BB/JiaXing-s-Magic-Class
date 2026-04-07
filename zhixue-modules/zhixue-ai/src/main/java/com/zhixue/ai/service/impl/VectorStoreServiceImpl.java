package com.zhixue.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.ai.service.EmbeddingService;
import com.zhixue.ai.service.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * 向量检索服务的实现类。
 * 这个类实现了基于 Elasticsearch 的向量存储和检索功能。
 * 需要在 Elasticsearch 中预先创建带向量字段的索引，比如 zhixue_kb。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreServiceImpl implements VectorStoreService {

    private final RestClient restClient;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Value("${zhixue.integration.ai.mode:sandbox}")
    private String aiMode;

    /**
     * Elasticsearch 索引名称
     */
    private static final String INDEX = "zhixue_kb";
    private final ConcurrentMap<Long, String> localChunks = new ConcurrentHashMap<>();

    /**
     * 把知识分片存入搜索引擎。
     * 先把文字转换成向量，然后一起存入 Elasticsearch。
     * @param chunkId 本地数据库中的分片编号
     * @param text 分片的文本内容
     * @return 在 Elasticsearch 中的文档编号
     */
    @Override
    public String indexChunk(Long chunkId, String text) {
        if (chunkId == null || !StringUtils.hasText(text)) {
            return "invalid";
        }
        if (isStubMode()) {
            localChunks.put(chunkId, text);
            return "stub-" + chunkId;
        }
        try {
            log.info("准备索引分片: chunkId={}, ES Nodes: {}", chunkId, restClient.getNodes());
            // 把文字转换成向量
            List<Float> vec = embeddingService.embed(text);
            if (vec == null || vec.isEmpty()) {
                localChunks.put(chunkId, text);
                return "local-" + chunkId;
            }
            // 构造要存入的文档数据
            Map<String, Object> doc = Map.of(
                    "chunkId", chunkId,
                    "content", text,
                    "vector", vec
            );
            Request req = new Request("POST", "/" + INDEX + "/_doc");
            req.setJsonEntity(objectMapper.writeValueAsString(doc));
            Response resp = restClient.performRequest(req);
            JsonNode root = objectMapper.readTree(resp.getEntity().getContent());
            return root.path("_id").asText();
        } catch (Exception e) {
            log.error("向 ES 索引知识分片失败 chunkId={}", chunkId, e);
            localChunks.put(chunkId, text);
            return "local-" + chunkId;
        }
    }

    /**
     * 按向量相似度搜索知识库。
     * 先把问题转换成向量，然后在 Elasticsearch 中找最相似的内容。
     * @param query 用户的问题
     * @param topK 返回最相似的前几条结果
     * @return 匹配的分片编号列表，按相似度从高到低排序
     */
    @Override
    public List<Long> search(String query, int topK) {
        if (!StringUtils.hasText(query) || topK <= 0) {
            return List.of();
        }
        if (isStubMode()) {
            return localSearch(query, topK);
        }
        // 把问题转换成向量
        List<Float> vec = embeddingService.embed(query);
        if (vec == null || vec.isEmpty()) {
            return localSearch(query, topK);
        }
        try {
            log.info("开始向量检索, 目标 ES 节点: {}", restClient.getNodes());
            // 构造向量搜索参数
            Map<String, Object> knn = Map.of(
                    "field", "vector",
                    "query_vector", vec,
                    "k", topK,
                    "num_candidates", Math.max(50, topK * 5)
            );
            Map<String, Object> body = Map.of("knn", knn,
                    "_source", List.of("chunkId"));
            Request req = new Request("POST", "/" + INDEX + "/_search");
            req.setJsonEntity(objectMapper.writeValueAsString(body));
            Response resp = restClient.performRequest(req);
            JsonNode root = objectMapper.readTree(resp.getEntity().getContent());
            JsonNode hits = root.path("hits").path("hits");
            List<Long> ids = new ArrayList<>();
            for (JsonNode hit : hits) {
                long chunkId = hit.path("_source").path("chunkId").asLong();
                ids.add(chunkId);
            }
            return ids.stream().distinct().collect(Collectors.toList());
        } catch (Exception e) {
            log.error("向量检索失败 query={}", query, e);
            return localSearch(query, topK);
        }
    }

    private List<Long> localSearch(String query, int topK) {
        String normalized = query.toLowerCase();
        return localChunks.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .sorted(Comparator.<Map.Entry<Long, String>>comparingInt(entry ->
                        lexicalScore(entry.getValue().toLowerCase(), normalized)).reversed())
                .limit(topK)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private int lexicalScore(String text, String query) {
        if (text.contains(query)) {
            return query.length() * 10;
        }
        int score = 0;
        for (char c : query.toCharArray()) {
            if (text.indexOf(c) >= 0) {
                score++;
            }
        }
        return score;
    }

    private boolean isStubMode() {
        return "stub".equalsIgnoreCase(aiMode);
    }
}

