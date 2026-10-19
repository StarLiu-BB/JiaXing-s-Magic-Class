package com.zhixue.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.ai.service.KnowledgeBaseService;
import org.apache.http.HttpEntity;
import org.apache.http.ProtocolVersion;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicStatusLine;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 知识库索引初始化。
 *
 * <p>D34：项目最初没有任何 dense_vector 映射，knn 查询从未真正执行。
 * 本次又出现两个问题：换成本地 1024 维模型后，已存在的 1536 维索引若"存在即跳过"就会让所有写入被 ES 拒绝；
 * 种子知识只写进了 MySQL、从未写入 ES，永远检索不到。</p>
 */
class KnowledgeIndexInitializerTest {

    private RestClient restClient;
    private KnowledgeBaseService knowledgeBaseService;
    private KnowledgeIndexInitializer initializer;
    private final List<String> calls = new ArrayList<>();

    @BeforeEach
    void setUp() {
        restClient = mock(RestClient.class);
        knowledgeBaseService = mock(KnowledgeBaseService.class);
        initializer = new KnowledgeIndexInitializer(restClient, new ObjectMapper(), knowledgeBaseService);
        ReflectionTestUtils.setField(initializer, "dimension", 1024);
        ReflectionTestUtils.setField(initializer, "aiMode", "real");
    }

    private static Response response(int status, String json) {
        Response response = mock(Response.class);
        HttpEntity entity = new StringEntity(json, ContentType.APPLICATION_JSON);
        when(response.getEntity()).thenReturn(entity);
        when(response.getStatusLine()).thenReturn(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), status, ""));
        return response;
    }

    /** 按 "METHOD /path" 返回预设响应，并记录调用顺序 */
    private void es(Map<String, Object> routes) throws Exception {
        // 预先构造好所有响应：在 thenAnswer 内部再调用 when() 会破坏 Mockito 的打桩状态
        Response notFound = response(404, "{}");
        Response empty = response(200, "{}");
        ResponseException notFoundException = mock(ResponseException.class);
        when(notFoundException.getResponse()).thenReturn(notFound);
        when(restClient.performRequest(any(Request.class))).thenAnswer(inv -> {
            Request r = inv.getArgument(0);
            String key = r.getMethod() + " " + r.getEndpoint();
            calls.add(key);
            Object reply = routes.get(key);
            if (reply instanceof Integer status && status == 404) {
                throw notFoundException;
            }
            return reply == null ? empty : reply;
        });
    }

    private static String mapping(int dims) {
        return "{\"zhixue_kb\":{\"mappings\":{\"properties\":{\"vector\":{\"type\":\"dense_vector\",\"dims\":" + dims + "}}}}}";
    }

    @Test
    void createsIndexWithConfiguredDimsAndBackfillsFromDatabaseWhenMissing() throws Exception {
        es(Map.of("GET /zhixue_kb/_mapping", 404));

        initializer.ensureIndex();

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(restClient, times(2)).performRequest(captor.capture());
        Request put = captor.getAllValues().get(1);
        assertThat(put.getMethod() + " " + put.getEndpoint()).isEqualTo("PUT /zhixue_kb");
        String body = new String(put.getEntity().getContent().readAllBytes());
        assertThat(body).contains("\"dense_vector\"").contains("\"dims\":1024").contains("\"cosine\"")
                .contains("\"courseId\"");
        verify(knowledgeBaseService).reindexAll();
    }

    @Test
    void recreatesIndexWhenDimensionChanged() throws Exception {
        es(Map.of("GET /zhixue_kb/_mapping", response(200, mapping(1536))));

        initializer.ensureIndex();

        assertThat(calls).containsExactly("GET /zhixue_kb/_mapping", "DELETE /zhixue_kb", "PUT /zhixue_kb");
        verify(knowledgeBaseService).reindexAll();
    }

    @Test
    void keepsExistingIndexWithMatchingDimension() throws Exception {
        es(Map.of("GET /zhixue_kb/_mapping", response(200, mapping(1024)),
                "GET /zhixue_kb/_count", response(200, "{\"count\":12}")));

        initializer.ensureIndex();

        assertThat(calls).doesNotContain("DELETE /zhixue_kb", "PUT /zhixue_kb");
        verify(knowledgeBaseService, never()).reindexAll();
    }

    @Test
    void backfillsEmptyIndexWithMatchingDimension() throws Exception {
        es(Map.of("GET /zhixue_kb/_mapping", response(200, mapping(1024)),
                "GET /zhixue_kb/_count", response(200, "{\"count\":0}")));

        initializer.ensureIndex();

        assertThat(calls).doesNotContain("DELETE /zhixue_kb");
        verify(knowledgeBaseService).reindexAll();
    }

    @Test
    void failureMustNotBlockStartup() throws Exception {
        when(restClient.performRequest(any(Request.class))).thenThrow(new java.io.IOException("ES down"));
        initializer.ensureIndex();
        verify(knowledgeBaseService, never()).reindexAll();
    }

    @Test
    void stubModeSkipsEverything() throws Exception {
        ReflectionTestUtils.setField(initializer, "aiMode", "stub");
        initializer.ensureIndex();
        verify(restClient, never()).performRequest(any(Request.class));
    }
}
