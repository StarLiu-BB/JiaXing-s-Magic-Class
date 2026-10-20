package com.zhixue.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixue.ai.domain.entity.KnowledgeChunk;
import com.zhixue.ai.domain.entity.KnowledgeDocument;
import com.zhixue.ai.mapper.KnowledgeChunkMapper;
import com.zhixue.ai.mapper.KnowledgeDocumentMapper;
import com.zhixue.ai.rag.TextSplitter;
import com.zhixue.ai.service.KnowledgeBaseService;
import com.zhixue.ai.service.VectorStoreService;
import com.zhixue.ai.service.VectorStoreService.ChunkDoc;
import com.zhixue.ai.service.VectorStoreService.ScoredChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 知识库：上传文档切分入库并写入向量索引；按问题检索相关资料。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    /** 一次批量向量化的分片数：本地模型批量远快于逐条，但单批过大占用内存 */
    private static final int REINDEX_BATCH = 32;

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final TextSplitter textSplitter;
    private final VectorStoreService vectorStoreService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocument createFromText(String title, String source, String tags, String content) {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setTitle(title);
        doc.setSource(source);
        doc.setTags(tags);
        doc.setStatus(1);
        documentMapper.insert(doc);

        Long courseId = KnowledgeBaseService.courseIdOf(source);
        List<ChunkDoc> pending = new ArrayList<>();
        int index = 0;
        for (String chunkText : textSplitter.split(content)) {
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setDocumentId(doc.getId());
            chunk.setChunkIndex(index++);
            chunk.setContent(chunkText);
            chunk.setEsId(null);
            chunkMapper.insert(chunk);
            pending.add(new ChunkDoc(chunk.getId(), doc.getId(), courseId, chunkText));
        }
        // 一次批量向量化；real 模式失败会抛出并回滚上面的写库
        int indexed = vectorStoreService.indexChunks(pending);
        markIndexed(pending, indexed);
        log.info("知识库文档入库 id={} 分片={} 已写入向量索引={}", doc.getId(), pending.size(), indexed);
        return doc;
    }

    @Override
    public List<String> retrieveContext(String query, int topK, Long courseId) {
        List<Long> chunkIds = vectorStoreService.search(query, topK, courseId);
        if (chunkIds.isEmpty()) {
            return List.of();
        }
        Map<Long, KnowledgeChunk> chunks = chunkMapper.selectBatchIds(chunkIds).stream()
                .collect(Collectors.toMap(KnowledgeChunk::getId, Function.identity()));
        Map<Long, KnowledgeDocument> docs = chunks.isEmpty() ? Map.of() : documentMapper
                .selectBatchIds(chunks.values().stream().map(KnowledgeChunk::getDocumentId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(KnowledgeDocument::getId, Function.identity()));
        List<String> texts = new ArrayList<>();
        for (Long id : chunkIds) {
            KnowledgeChunk chunk = chunks.get(id);
            KnowledgeDocument doc = chunk == null ? null : docs.get(chunk.getDocumentId());
            // 检索结果里可能残留已删除/停用文档的分片，以数据库为准
            if (doc != null && Integer.valueOf(1).equals(doc.getStatus())) {
                texts.add("《" + doc.getTitle() + "》" + chunk.getContent());
            }
        }
        return texts;
    }

    @Override
    public List<RetrievedChunk> retrieveScored(String query, List<Float> queryVector, int topK, Long courseId) {
        List<ScoredChunk> hits = vectorStoreService.searchWithScores(query, queryVector, topK, courseId);
        if (hits.isEmpty()) {
            return List.of();
        }
        Map<Long, KnowledgeChunk> chunks = chunkMapper.selectBatchIds(hits.stream().map(ScoredChunk::chunkId).toList())
                .stream()
                .collect(Collectors.toMap(KnowledgeChunk::getId, Function.identity()));
        Map<Long, KnowledgeDocument> docs = chunks.isEmpty() ? Map.of() : documentMapper
                .selectBatchIds(chunks.values().stream().map(KnowledgeChunk::getDocumentId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(KnowledgeDocument::getId, Function.identity()));
        List<RetrievedChunk> result = new ArrayList<>();
        for (ScoredChunk hit : hits) {
            KnowledgeChunk chunk = chunks.get(hit.chunkId());
            KnowledgeDocument doc = chunk == null ? null : docs.get(chunk.getDocumentId());
            if (doc != null && Integer.valueOf(1).equals(doc.getStatus())) {
                result.add(new RetrievedChunk(hit.chunkId(), KnowledgeBaseService.courseIdOf(doc.getSource()),
                        hit.cosine(), doc.getTitle(), chunk.getContent()));
            }
        }
        return result;
    }

    @Override
    public int reindexAll() {
        List<KnowledgeDocument> documents = documentMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDocument>().eq(KnowledgeDocument::getStatus, 1));
        int indexed = 0;
        for (KnowledgeDocument doc : documents) {
            Long courseId = KnowledgeBaseService.courseIdOf(doc.getSource());
            List<KnowledgeChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<KnowledgeChunk>()
                    .eq(KnowledgeChunk::getDocumentId, doc.getId())
                    .orderByAsc(KnowledgeChunk::getChunkIndex));
            for (int from = 0; from < chunks.size(); from += REINDEX_BATCH) {
                List<ChunkDoc> batch = chunks.subList(from, Math.min(from + REINDEX_BATCH, chunks.size())).stream()
                        .map(c -> new ChunkDoc(c.getId(), doc.getId(), courseId, c.getContent()))
                        .toList();
                int n = vectorStoreService.indexChunks(batch);
                markIndexed(batch, n);
                indexed += n;
            }
        }
        log.info("知识库向量索引重建完成：文档 {} 篇，写入分片 {} 个", documents.size(), indexed);
        return indexed;
    }

    @Override
    public int indexPending() {
        List<KnowledgeChunk> pending = chunkMapper.selectList(new LambdaQueryWrapper<KnowledgeChunk>()
                .isNull(KnowledgeChunk::getEsId)
                .orderByAsc(KnowledgeChunk::getDocumentId)
                .orderByAsc(KnowledgeChunk::getChunkIndex));
        if (pending.isEmpty()) {
            return 0;
        }
        Map<Long, KnowledgeDocument> enabled = documentMapper
                .selectBatchIds(pending.stream().map(KnowledgeChunk::getDocumentId).distinct().toList())
                .stream()
                .filter(doc -> Integer.valueOf(1).equals(doc.getStatus()))
                .collect(Collectors.toMap(KnowledgeDocument::getId, Function.identity()));
        List<ChunkDoc> docs = pending.stream()
                .filter(c -> enabled.containsKey(c.getDocumentId()))
                .filter(c -> {
                    // 空内容写不进向量索引；留在批里会让整批都不回填 es_id，每次启动重复向量化
                    if (StringUtils.hasText(c.getContent())) {
                        return true;
                    }
                    log.warn("分片内容为空，跳过写入向量索引 chunkId={} documentId={}", c.getId(), c.getDocumentId());
                    return false;
                })
                .map(c -> new ChunkDoc(c.getId(), c.getDocumentId(),
                        KnowledgeBaseService.courseIdOf(enabled.get(c.getDocumentId()).getSource()), c.getContent()))
                .toList();
        int indexed = 0;
        for (int from = 0; from < docs.size(); from += REINDEX_BATCH) {
            List<ChunkDoc> batch = docs.subList(from, Math.min(from + REINDEX_BATCH, docs.size()));
            int n = vectorStoreService.indexChunks(batch);
            markIndexed(batch, n);
            indexed += n;
        }
        log.info("补齐向量索引：待补分片 {} 个（其中属于启用文档且有内容的 {} 个），写入 {} 个",
                pending.size(), docs.size(), indexed);
        return indexed;
    }

    /** es_id 与 ES 文档 ID 一致（即 chunkId）；未写入向量索引的保持为空，便于排查 */
    private void markIndexed(List<ChunkDoc> batch, int indexed) {
        if (indexed != batch.size()) {
            return;
        }
        for (ChunkDoc c : batch) {
            KnowledgeChunk update = new KnowledgeChunk();
            update.setId(c.chunkId());
            update.setEsId(String.valueOf(c.chunkId()));
            chunkMapper.updateById(update);
        }
    }
}
