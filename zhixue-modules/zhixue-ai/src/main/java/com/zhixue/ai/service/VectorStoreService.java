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
}
