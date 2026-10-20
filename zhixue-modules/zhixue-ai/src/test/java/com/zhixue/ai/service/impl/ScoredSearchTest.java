package com.zhixue.ai.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixue.ai.domain.entity.KnowledgeChunk;
import com.zhixue.ai.domain.entity.KnowledgeDocument;
import com.zhixue.ai.mapper.KnowledgeChunkMapper;
import com.zhixue.ai.mapper.KnowledgeDocumentMapper;
import com.zhixue.ai.rag.TextSplitter;
import com.zhixue.ai.service.EmbeddingService;
import com.zhixue.ai.service.KnowledgeBaseService.RetrievedChunk;
import com.zhixue.ai.service.VectorStoreService;
import com.zhixue.ai.service.VectorStoreService.ChunkDoc;
import com.zhixue.ai.service.VectorStoreService.ScoredChunk;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 检索分数必须与门槛同一刻度：索引按 cosine 建立时 ES 的 _score = (1 + cos) / 2，
 * 而 kNN 的 similarity 参数与推荐门槛都是原始余弦。直接拿 _score 比 0.55，
 * 「今天中午吃什么」（_score 0.64–0.66，余弦 0.28–0.32）也会推荐出课程。
 */
class ScoredSearchTest {

    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, KnowledgeChunk.class);
        TableInfoHelper.initTableInfo(assistant, KnowledgeDocument.class);
    }

    private final RestClient restClient = mock(RestClient.class);
    private final EmbeddingService embeddingService = mock(EmbeddingService.class);

    private VectorStoreServiceImpl vectorStore(String mode) {
        VectorStoreServiceImpl service = new VectorStoreServiceImpl(restClient, embeddingService);
        ReflectionTestUtils.setField(service, "aiMode", mode);
        ReflectionTestUtils.setField(service, "minSimilarity", 0.45);
        return service;
    }

    @Test
    void convertsEsScoreToCosineAndKeepsCourseOfEachHit() throws Exception {
        Response response = mock(Response.class);
        when(response.getEntity()).thenReturn(new StringEntity("""
                {"hits":{"hits":[
                  {"_score":0.66,"_source":{"chunkId":92018,"courseId":2007}},
                  {"_score":0.80,"_source":{"chunkId":92009}},
                  {"_score":0.70,"_source":{"chunkId":92018,"courseId":2007}}
                ]}}""", ContentType.APPLICATION_JSON));
        when(restClient.performRequest(any(Request.class))).thenReturn(response);

        List<ScoredChunk> hits = vectorStore("real").searchWithScores("线程池", List.of(1f, 0f), 8, 2007L);

        assertThat(hits).extracting(ScoredChunk::chunkId).containsExactly(92018L, 92009L); // 同一分片只保留一次
        assertThat(hits.get(0).courseId()).isEqualTo(2007L);
        assertThat(hits.get(0).cosine()).isCloseTo(0.32, within(1e-9));
        assertThat(hits.get(1).courseId()).isNull();
        assertThat(hits.get(1).cosine()).isCloseTo(0.60, within(1e-9));

        ArgumentCaptor<Request> request = ArgumentCaptor.forClass(Request.class);
        verify(restClient).performRequest(request.capture());
        String body = new String(request.getValue().getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(body).contains("\"similarity\":0.45").contains("\"query_vector\":[1.0,0.0]")
                .contains("\"term\":{\"courseId\":2007}")
                // 推荐靠每条命中的课程归属：_source 必须取回 courseId，不能只取 chunkId
                .contains("\"_source\":[\"chunkId\",\"courseId\"]");
        // 向量由调用方算好传入，这里不能再向量化一次
        verify(embeddingService, never()).embed(anyString());
    }

    @Test
    void emptyVectorFallsBackToLexicalWithZeroCosine() {
        when(embeddingService.embedBatch(any())).thenReturn(List.of());
        VectorStoreServiceImpl service = vectorStore("sandbox");
        service.indexChunks(List.of(new ChunkDoc(1L, 10L, 2006L, "HashMap 为什么线程不安全")));

        List<ScoredChunk> hits = service.searchWithScores("HashMap", List.of(), 5, null);

        assertThat(hits).containsExactly(new ScoredChunk(1L, 2006L, 0.0));
        verify(embeddingService, never()).embed(anyString());
    }

    /** real 模式下检索失败必须如实报错，不能降级成「没有资料」悄悄回答 */
    @Test
    void realModeSearchFailureIs503() throws Exception {
        when(restClient.performRequest(any(Request.class))).thenThrow(new java.io.IOException("ES down"));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> vectorStore("real").searchWithScores("线程池", List.of(1f, 0f), 8, null))
                .isInstanceOf(com.zhixue.common.core.exception.ServiceException.class)
                .hasMessage("AI 服务暂不可用，请稍后再试");
    }

    @Test
    void retrieveScoredKeepsOrderUsesDatabaseCourseAndDropsDisabledDocuments() {
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
        VectorStoreService vectorStore = mock(VectorStoreService.class);
        KnowledgeBaseServiceImpl service = new KnowledgeBaseServiceImpl(
                documentMapper, chunkMapper, mock(TextSplitter.class), vectorStore);
        when(vectorStore.searchWithScores(eq("HashMap"), any(), eq(8), isNull())).thenReturn(List.of(
                new ScoredChunk(92014L, 2006L, 0.71),
                new ScoredChunk(92099L, 2006L, 0.66),
                // ES 里没有课程字段（例如写入时 source 还没归属课程），归属必须以数据库为准
                new ScoredChunk(92018L, null, 0.58)));
        when(chunkMapper.selectBatchIds(any())).thenReturn(List.of(
                chunk(92014L, 91014L), chunk(92099L, 91099L), chunk(92018L, 91018L)));
        when(documentMapper.selectBatchIds(any())).thenReturn(List.of(
                document(91014L, "Java 集合与泛型 · HashMap 深入", "course:2006", 1),
                document(91099L, "已停用资料", "course:2006", 0),
                document(91018L, "Java 并发编程实战 · 线程池", "course:2007", 1)));

        List<RetrievedChunk> hits = service.retrieveScored("HashMap", List.of(1f), 8, null);

        assertThat(hits).extracting(RetrievedChunk::chunkId).containsExactly(92014L, 92018L);
        assertThat(hits).extracting(RetrievedChunk::courseId).containsExactly(2006L, 2007L);
        assertThat(hits).extracting(RetrievedChunk::cosine).containsExactly(0.71, 0.58);
        assertThat(hits.get(0).title()).isEqualTo("Java 集合与泛型 · HashMap 深入");
        assertThat(hits.get(0).content()).isEqualTo("内容 92014");
    }

    private static KnowledgeChunk chunk(long id, long documentId) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId(id);
        chunk.setDocumentId(documentId);
        chunk.setContent("内容 " + id);
        return chunk;
    }

    private static KnowledgeDocument document(long id, String title, String source, int status) {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(id);
        doc.setTitle(title);
        doc.setSource(source);
        doc.setStatus(status);
        return doc;
    }
}
