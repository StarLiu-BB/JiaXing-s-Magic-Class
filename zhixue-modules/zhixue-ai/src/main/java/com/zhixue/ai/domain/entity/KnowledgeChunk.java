package com.zhixue.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixue.common.mybatis.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库分片文本实体类。
 * 这个类对应数据库中的知识库分片表。
 * 因为文档太长，需要切成小段存储，每个小段就是一条分片记录。
 * 这样 AI 查询时可以找到最相关的小段内容，而不是整篇文档。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kb_chunk")
public class KnowledgeChunk extends BaseEntity {

    /**
     * 所属文档的编号
     */
    private Long documentId;

    /**
     * 分片序号，表示这是文档的第几段
     */
    private Integer chunkIndex;

    /**
     * 分片内容，就是文档切开后的一段文字
     */
    private String content;

    /**
     * 搜索引擎中的编号，用于在 Elasticsearch 中快速检索
     */
    private String esId;
}


