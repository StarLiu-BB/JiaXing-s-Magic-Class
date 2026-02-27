package com.zhixue.ai.service;

import java.util.List;

/**
 * 向量检索服务接口。
 * 这个接口定义了向量存储和检索的方法，基于 Elasticsearch 实现。
 * 向量检索可以找到和问题最相关的知识库内容。
 */
public interface VectorStoreService {

    /**
     * 把知识分片存入搜索引擎。
     * 存入时会自动生成向量，方便后续按相似度搜索。
     * @param chunkId 本地数据库中的分片编号
     * @param text 分片的文本内容
     * @return 在搜索引擎中的文档编号
     */
    String indexChunk(Long chunkId, String text);

    /**
     * 按向量相似度搜索知识库。
     * 把问题转换成向量，然后在知识库中找最相似的内容。
     * @param query 用户的问题
     * @param topK 返回最相似的前几条结果
     * @return 匹配的分片编号列表，按相似度从高到低排序
     */
    List<Long> search(String query, int topK);
}


