package com.zhixue.ai.service;

import com.zhixue.ai.domain.entity.KnowledgeDocument;

import java.util.List;

/**
 * 知识库服务。
 */
public interface KnowledgeBaseService {

    /** 文档来源为 "course:{课程ID}" 时归属该课程，否则为通用知识 */
    String COURSE_SOURCE_PREFIX = "course:";

    /**
     * 从文本创建知识库文档：切分、写库、写向量索引。real 模式下索引失败则整体回滚。
     */
    KnowledgeDocument createFromText(String title, String source, String tags, String content);

    /**
     * 检索与问题相关的资料。
     *
     * @param courseId 非空时只检索该课程资料与通用资料
     * @return 每条形如「《文档标题》分片内容」，按相关度排序
     */
    List<String> retrieveContext(String query, int topK, Long courseId);

    /**
     * 把数据库中全部启用文档的分片重新写入向量索引。索引首次创建或因维度变化重建后调用。
     *
     * @return 写入向量索引的分片数
     */
    int reindexAll();

    /**
     * 把数据库中存在、但还没写入向量索引（{@code es_id} 为空）且所属文档启用的分片补写进索引。
     *
     * <p>增量脚本只写 MySQL；索引已存在时启动流程不重建，不补齐的话新资料永远检索不到。
     * 分片以 chunkId 作 ES 文档 ID，重复执行没有副作用。</p>
     *
     * @return 本次写入向量索引的分片数
     */
    int indexPending();

    static Long courseIdOf(String source) {
        if (source == null || !source.startsWith(COURSE_SOURCE_PREFIX)) {
            return null;
        }
        try {
            return Long.parseLong(source.substring(COURSE_SOURCE_PREFIX.length()).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
