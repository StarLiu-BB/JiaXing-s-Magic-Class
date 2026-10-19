package com.zhixue.ai.service.impl;

import com.zhixue.ai.service.EmbeddingService;
import com.zhixue.ai.service.VectorStoreService.ChunkDoc;
import com.zhixue.common.core.exception.ServiceException;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VectorStoreServiceImplTest {

    private final RestClient restClient = mock(RestClient.class);
    private final EmbeddingService embeddingService = mock(EmbeddingService.class);

    private VectorStoreServiceImpl newService(String mode) {
        VectorStoreServiceImpl service = new VectorStoreServiceImpl(restClient, embeddingService);
        ReflectionTestUtils.setField(service, "aiMode", mode);
        ReflectionTestUtils.setField(service, "minSimilarity", 0.45);
        return service;
    }

    private static Response ok(String json) {
        Response response = mock(Response.class);
        when(response.getEntity()).thenReturn(new StringEntity(json, ContentType.APPLICATION_JSON));
        return response;
    }

    private static List<Float> vector() {
        return Collections.nCopies(1024, 0.1f);
    }

    private static String body(Request request) throws Exception {
        return new String(request.getEntity().getContent().readAllBytes());
    }

    @Test
    void stubModeIndexesAndSearchesLocally() throws Exception {
        VectorStoreServiceImpl service = newService("stub");

        service.indexChunks(List.of(new ChunkDoc(1001L, 1L, null, "Spring Cloud 网关认证实战")));

        assertThat(service.search("网关认证", 3, null)).containsExactly(1001L);
        verify(restClient, never()).performRequest(any(Request.class));
    }

    @Test
    void localSearchRespectsCourseScopeAndDropsZeroScoreHits() {
        VectorStoreServiceImpl service = newService("stub");
        service.indexChunks(List.of(
                new ChunkDoc(1L, 1L, 2001L, "网关鉴权"),
                new ChunkDoc(2L, 2L, 2002L, "网关限流"),
                new ChunkDoc(3L, 3L, null, "网关通用知识"),
                new ChunkDoc(4L, 4L, null, "数据库索引优化")));

        assertThat(service.search("网关", 5, 2001L)).containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    void bulkIndexesWithChunkIdAsDocumentIdInOneBatchEmbedding() throws Exception {
        when(embeddingService.embedBatch(anyList())).thenReturn(List.of(vector(), vector()));
        Response bulkOk = ok("{\"errors\":false}");
        when(restClient.performRequest(any(Request.class))).thenReturn(bulkOk);

        int n = newService("real").indexChunks(List.of(
                new ChunkDoc(11L, 1L, 2001L, "甲"), new ChunkDoc(12L, 1L, 2001L, "乙")));

        assertThat(n).isEqualTo(2);
        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(restClient).performRequest(captor.capture());
        assertThat(captor.getValue().getEndpoint()).isEqualTo("/_bulk");
        // 以 chunkId 作文档 ID：重建索引可安全重放，不会产生重复文档
        assertThat(body(captor.getValue())).contains("\"_id\":\"11\"").contains("\"_id\":\"12\"")
                .contains("\"courseId\":2001");
        verify(embeddingService, never()).embed(anyString());
    }

    @Test
    void searchFiltersByCourseAndAppliesSimilarityThreshold() throws Exception {
        when(embeddingService.embed(anyString())).thenReturn(vector());
        Response hits = ok("{\"hits\":{\"hits\":[{\"_source\":{\"chunkId\":7}},{\"_source\":{\"chunkId\":7}}]}}");
        when(restClient.performRequest(any(Request.class))).thenReturn(hits);

        List<Long> ids = newService("real").search("网关怎么鉴权", 5, 2001L);

        assertThat(ids).containsExactly(7L);
        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(restClient).performRequest(captor.capture());
        String query = body(captor.getValue());
        assertThat(query).contains("\"similarity\":0.45").contains("\"courseId\":2001").contains("must_not");
    }

    @Test
    void realModeSurfacesEsFailureInsteadOfSilentlyDegrading() throws Exception {
        when(embeddingService.embedBatch(anyList())).thenReturn(List.of(vector()));
        when(restClient.performRequest(any(Request.class))).thenThrow(new java.io.IOException("ES down"));

        assertThatThrownBy(() -> newService("real").indexChunks(List.of(new ChunkDoc(1L, 1L, null, "甲"))))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void realModeRejectsPartialBulkFailure() throws Exception {
        when(embeddingService.embedBatch(anyList())).thenReturn(List.of(vector()));
        Response partial = ok("{\"errors\":true,\"items\":[]}");
        when(restClient.performRequest(any(Request.class))).thenReturn(partial);

        assertThatThrownBy(() -> newService("real").indexChunks(List.of(new ChunkDoc(1L, 1L, null, "甲"))))
                .isInstanceOf(ServiceException.class);
    }
}
