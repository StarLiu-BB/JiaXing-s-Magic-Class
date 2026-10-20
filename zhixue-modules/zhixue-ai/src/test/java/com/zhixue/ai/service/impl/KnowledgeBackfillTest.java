package com.zhixue.ai.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixue.ai.domain.entity.KnowledgeChunk;
import com.zhixue.ai.domain.entity.KnowledgeDocument;
import com.zhixue.ai.mapper.KnowledgeChunkMapper;
import com.zhixue.ai.mapper.KnowledgeDocumentMapper;
import com.zhixue.ai.rag.TextSplitter;
import com.zhixue.ai.service.VectorStoreService;
import com.zhixue.ai.service.VectorStoreService.ChunkDoc;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 增量脚本只写 MySQL；索引已存在且维度一致时启动流程原本直接跳过，
 * 新资料于是永远进不了向量索引、永远检索不到。
 */
class KnowledgeBackfillTest {

    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, KnowledgeChunk.class);
        TableInfoHelper.initTableInfo(assistant, KnowledgeDocument.class);
    }

    private final KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
    private final KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
    private final VectorStoreService vectorStoreService = mock(VectorStoreService.class);
    private final KnowledgeBaseServiceImpl service = new KnowledgeBaseServiceImpl(
            documentMapper, chunkMapper, mock(TextSplitter.class), vectorStoreService);

    @Test
    @SuppressWarnings("unchecked")
    void indexesOnlyChunksOfEnabledDocumentsAndMarksThem() {
        when(chunkMapper.selectList(any())).thenReturn(List.of(
                chunk(92014L, 91014L, "HashMap 扩容"), chunk(92099L, 91099L, "停用文档的分片")));
        when(documentMapper.selectBatchIds(any())).thenReturn(List.of(
                document(91014L, "course:2006", 1), document(91099L, "course:2006", 0)));
        when(vectorStoreService.indexChunks(anyList())).thenReturn(1);

        assertThat(service.indexPending()).isEqualTo(1);

        ArgumentCaptor<List<ChunkDoc>> batch = ArgumentCaptor.forClass(List.class);
        verify(vectorStoreService).indexChunks(batch.capture());
        assertThat(batch.getValue()).extracting(ChunkDoc::chunkId).containsExactly(92014L);
        assertThat(batch.getValue().get(0).courseId()).isEqualTo(2006L);
        ArgumentCaptor<KnowledgeChunk> marked = ArgumentCaptor.forClass(KnowledgeChunk.class);
        verify(chunkMapper).updateById(marked.capture());
        assertThat(marked.getValue().getEsId()).isEqualTo("92014");
    }

    @Test
    void doesNothingWhenEveryChunkIsIndexed() {
        when(chunkMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.indexPending()).isZero();
        verify(vectorStoreService, never()).indexChunks(anyList());
    }

    /**
     * 增量 SQL 绕过 TextSplitter 直接写库，可能写进空内容的分片。indexChunks 会跳过它，
     * 返回数小于批大小，于是整批都不回填 es_id——同批的其他分片每次启动都会被重新向量化。
     */
    @Test
    @SuppressWarnings("unchecked")
    void blankChunkDoesNotBlockTheRestOfTheBatchFromBeingMarked() {
        when(chunkMapper.selectList(any())).thenReturn(List.of(
                chunk(92014L, 91014L, "HashMap 扩容"), chunk(92015L, 91014L, "  ")));
        when(documentMapper.selectBatchIds(any())).thenReturn(List.of(document(91014L, "course:2006", 1)));
        // 与真实 indexChunks 一致：只写入有内容的分片，返回写入数
        when(vectorStoreService.indexChunks(anyList())).thenAnswer(invocation -> (int) ((List<ChunkDoc>) invocation
                .getArgument(0)).stream().filter(c -> c.content() != null && !c.content().isBlank()).count());

        assertThat(service.indexPending()).isEqualTo(1);

        ArgumentCaptor<KnowledgeChunk> marked = ArgumentCaptor.forClass(KnowledgeChunk.class);
        verify(chunkMapper).updateById(marked.capture());
        assertThat(marked.getValue().getEsId()).isEqualTo("92014");
    }

    private static KnowledgeChunk chunk(long id, long documentId, String content) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId(id);
        chunk.setDocumentId(documentId);
        chunk.setChunkIndex(0);
        chunk.setContent(content);
        return chunk;
    }

    private static KnowledgeDocument document(long id, String source, int status) {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(id);
        doc.setTitle("资料 " + id);
        doc.setSource(source);
        doc.setStatus(status);
        return doc;
    }
}
