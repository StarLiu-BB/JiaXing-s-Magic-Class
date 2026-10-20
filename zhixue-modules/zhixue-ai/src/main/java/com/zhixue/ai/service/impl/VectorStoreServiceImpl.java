package com.zhixue.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.ai.config.KnowledgeIndexInitializer;
import com.zhixue.ai.service.EmbeddingService;
import com.zhixue.ai.service.VectorStoreService;
import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.exception.ServiceException;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * 基于 Elasticsearch dense_vector 的知识分片存储与 kNN 检索。
 *
 * <p>stub/sandbox 下取不到向量时退回进程内的词法匹配（明确标注的降级）；
 * real 模式下向量化或 ES 失败直接抛出，不再静默降级。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorStoreServiceImpl implements VectorStoreService {

    private static final String INDEX = KnowledgeIndexInitializer.INDEX;

    private final RestClient restClient;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 词法降级用的本地副本：chunkId -> (courseId, content) */
    private final ConcurrentMap<Long, ChunkDoc> localChunks = new ConcurrentHashMap<>();

    @Value("${zhixue.integration.ai.mode:real}")
    private String aiMode;

    /**
     * kNN 最低余弦相似度。实测 qwen3-embedding:0.6b 相关问答约 0.75、无关约 0.21；
     * 低于阈值的"最近邻"只会把无关资料塞给模型，诱导它胡乱引用。
     */
    @Value("${ai.rag.min-similarity:0.45}")
    private double minSimilarity;

    @Override
    public int indexChunks(List<ChunkDoc> chunks) {
        List<ChunkDoc> valid = chunks == null ? List.of() : chunks.stream()
                .filter(c -> c.chunkId() != null && StringUtils.hasText(c.content()))
                .toList();
        if (valid.isEmpty()) {
            return 0;
        }
        valid.forEach(c -> localChunks.put(c.chunkId(), c));
        if (isStubMode()) {
            return 0;
        }
        List<List<Float>> vectors = embeddingService.embedBatch(valid.stream().map(ChunkDoc::content).toList());
        if (vectors.isEmpty()) {
            // sandbox 下模型不可用：保留本地词法副本
            return 0;
        }
        try {
            StringBuilder bulk = new StringBuilder();
            for (int i = 0; i < valid.size(); i++) {
                ChunkDoc c = valid.get(i);
                bulk.append(objectMapper.writeValueAsString(
                        Map.of("index", Map.of("_index", INDEX, "_id", String.valueOf(c.chunkId()))))).append('\n');
                Map<String, Object> doc = new HashMap<>();
                doc.put("chunkId", c.chunkId());
                doc.put("documentId", c.documentId());
                doc.put("courseId", c.courseId());
                doc.put("content", c.content());
                doc.put("vector", vectors.get(i));
                bulk.append(objectMapper.writeValueAsString(doc)).append('\n');
            }
            Request req = new Request("POST", "/_bulk");
            req.addParameter("refresh", "true");
            req.setJsonEntity(bulk.toString());
            JsonNode root = objectMapper.readTree(restClient.performRequest(req).getEntity().getContent());
            if (root.path("errors").asBoolean(false)) {
                throw new IllegalStateException("ES 批量写入部分失败: " + abbreviate(root.toString()));
            }
            return valid.size();
        } catch (Exception e) {
            return fail("向知识库索引写入失败", e, 0);
        }
    }

    @Override
    public List<Long> search(String query, int topK, Long courseId) {
        if (!StringUtils.hasText(query) || topK <= 0) {
            return List.of();
        }
        if (isStubMode()) {
            return localSearch(query, topK, courseId);
        }
        List<Float> vec = embeddingService.embed(query);
        if (vec.isEmpty()) {
            return localSearch(query, topK, courseId);
        }
        try {
            Map<String, Object> knn = knnClause(vec, topK, courseId);
            Request req = new Request("POST", "/" + INDEX + "/_search");
            req.setJsonEntity(objectMapper.writeValueAsString(Map.of("knn", knn, "_source", List.of("chunkId"))));
            Response resp = restClient.performRequest(req);
            JsonNode hits = objectMapper.readTree(resp.getEntity().getContent()).path("hits").path("hits");
            List<Long> ids = new ArrayList<>();
            for (JsonNode hit : hits) {
                ids.add(hit.path("_source").path("chunkId").asLong());
            }
            return ids.stream().distinct().collect(Collectors.toList());
        } catch (Exception e) {
            return fail("知识库检索失败", e, localSearch(query, topK, courseId));
        }
    }

    private List<Long> localSearch(String query, int topK, Long courseId) {
        String normalized = query.toLowerCase();
        return localChunks.values().stream()
                .filter(c -> courseId == null || c.courseId() == null || courseId.equals(c.courseId()))
                .map(c -> Map.entry(c.chunkId(), lexicalScore(c.content().toLowerCase(), normalized)))
                .filter(e -> e.getValue() > 0)
                .sorted(Comparator.<Map.Entry<Long, Integer>>comparingInt(Map.Entry::getValue).reversed())
                .limit(topK)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private Map<String, Object> knnClause(List<Float> vector, int topK, Long courseId) {
        Map<String, Object> knn = new LinkedHashMap<>();
        knn.put("field", "vector");
        knn.put("query_vector", vector);
        knn.put("k", topK);
        knn.put("num_candidates", Math.max(50, topK * 10));
        knn.put("similarity", minSimilarity);
        if (courseId != null) {
            // 本课程的资料 + 不属于任何课程的通用资料
            knn.put("filter", Map.of("bool", Map.of(
                    "should", List.of(
                            Map.of("term", Map.of("courseId", courseId)),
                            Map.of("bool", Map.of("must_not", Map.of("exists", Map.of("field", "courseId"))))),
                    "minimum_should_match", 1)));
        }
        return knn;
    }

    @Override
    public List<ScoredChunk> searchWithScores(String query, List<Float> queryVector, int topK, Long courseId) {
        if (!StringUtils.hasText(query) || topK <= 0) {
            return List.of();
        }
        if (isStubMode() || queryVector == null || queryVector.isEmpty()) {
            return lexicalScored(query, topK, courseId);
        }
        try {
            Request req = new Request("POST", "/" + INDEX + "/_search");
            req.setJsonEntity(objectMapper.writeValueAsString(Map.of(
                    "knn", knnClause(queryVector, topK, courseId),
                    "_source", List.of("chunkId", "courseId"))));
            JsonNode hits = objectMapper.readTree(restClient.performRequest(req).getEntity().getContent())
                    .path("hits").path("hits");
            Map<Long, ScoredChunk> unique = new LinkedHashMap<>();
            for (JsonNode hit : hits) {
                JsonNode source = hit.path("_source");
                long chunkId = source.path("chunkId").asLong();
                Long hitCourse = source.hasNonNull("courseId") ? source.path("courseId").asLong() : null;
                unique.putIfAbsent(chunkId, new ScoredChunk(chunkId, hitCourse, 2 * hit.path("_score").asDouble() - 1));
            }
            return new ArrayList<>(unique.values());
        } catch (Exception e) {
            return fail("知识库检索失败", e, lexicalScored(query, topK, courseId));
        }
    }

    /** 词法降级：没有可比的相似度，cosine 记 0，调用方据此不会出推荐 */
    private List<ScoredChunk> lexicalScored(String query, int topK, Long courseId) {
        return localSearch(query, topK, courseId).stream()
                .map(id -> new ScoredChunk(id, localChunks.get(id).courseId(), 0.0))
                .toList();
    }

    private int lexicalScore(String text, String query) {
        if (text.contains(query)) {
            return query.length() * 10;
        }
        int score = 0;
        for (char c : query.toCharArray()) {
            if (!Character.isWhitespace(c) && text.indexOf(c) >= 0) {
                score++;
            }
        }
        return score;
    }

    private <T> T fail(String message, Exception e, T degraded) {
        if (isRealMode()) {
            if (e instanceof ServiceException se) {
                throw se;
            }
            log.error(message, e);
            throw new ServiceException(HttpStatus.SERVICE_UNAVAILABLE, "AI 服务暂不可用，请稍后再试", e);
        }
        log.warn("{}，降级为本地词法检索：{}", message, e.getMessage());
        return degraded;
    }

    private static String abbreviate(String s) {
        return s.length() <= 300 ? s : s.substring(0, 300) + "…";
    }

    private boolean isStubMode() {
        return "stub".equalsIgnoreCase(aiMode);
    }

    private boolean isRealMode() {
        return "real".equalsIgnoreCase(aiMode);
    }
}
