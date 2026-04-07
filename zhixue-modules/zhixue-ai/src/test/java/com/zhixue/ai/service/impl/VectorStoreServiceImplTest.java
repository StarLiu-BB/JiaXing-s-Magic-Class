package com.zhixue.ai.service.impl;

import com.zhixue.ai.service.EmbeddingService;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class VectorStoreServiceImplTest {

    @Test
    void shouldIndexAndSearchFromLocalStoreInStubMode() {
        RestClient restClient = mock(RestClient.class);
        EmbeddingService embeddingService = mock(EmbeddingService.class);
        VectorStoreServiceImpl service = new VectorStoreServiceImpl(restClient, embeddingService);
        ReflectionTestUtils.setField(service, "aiMode", "stub");

        String id = service.indexChunk(1001L, "Spring Cloud 网关认证实战");
        List<Long> result = service.search("网关认证", 3);

        assertThat(id).isEqualTo("stub-1001");
        assertThat(result).contains(1001L);
    }
}
