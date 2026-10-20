package com.zhixue.ai.service;

import java.util.List;

/**
 * 知识分片的向量存储与检索。
 */
public interface VectorStoreService {

    /** 待写入的分片；courseId 为空表示通用知识，所有课程都可检索到 */
    record ChunkDoc(Long chunkId, Long documentId, Long courseId, String content) {
    }

    /**
     * 批量写入（以 chunkId 作为文档 ID，重复写入即覆盖，可安全重放）。
     *
     * @return 实际写入向量索引的条数；sandbox 下取不到向量时为 0（退回本地词法检索）
     */
    int indexChunks(List<ChunkDoc> chunks);

    /**
     * 语义检索。
     *
     * @param courseId 非空时只返回该课程的知识与通用知识
     * @return 命中的分片 ID，按相似度从高到低
     */
    List<Long> search(String query, int topK, Long courseId);

    /**
     * 带余弦分数的命中。
     *
     * @param courseId 分片所属课程（ES 中的值）；null 表示通用资料
     * @param cosine   余弦相似度，与 ai.rag.min-similarity、推荐门槛同一刻度
     */
    record ScoredChunk(Long chunkId, Long courseId, double cosine) {
    }

    /**
     * 用已算好的问句向量检索，返回余弦分数。问句只向量化一次，供回答与课时推荐复用。
     *
     * <p>索引按 cosine 建立时 ES 的 {@code _score} = (1 + cos) / 2，这里换算回 cos = 2 × _score − 1。</p>
     *
     * @param query       问句原文，仅在 queryVector 为空（sandbox 取不到向量）时用于词法降级
     * @param queryVector 问句向量
     * @return 按相关度降序，同一分片只出现一次；词法降级时 cosine 为 0
     */
    List<ScoredChunk> searchWithScores(String query, List<Float> queryVector, int topK, Long courseId);
}
