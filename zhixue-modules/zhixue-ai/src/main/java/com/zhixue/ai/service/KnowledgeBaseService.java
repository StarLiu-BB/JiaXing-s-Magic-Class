package com.zhixue.ai.service;

import com.zhixue.ai.domain.entity.KnowledgeDocument;

import java.util.List;

/**
 * 知识库服务接口。
 * 这个接口定义了知识库的管理方法，包括上传文档和检索内容。
 * 知识库用来存储课程资料、帮助文档等内容，供 AI 查询使用。
 */
public interface KnowledgeBaseService {

    /**
     * 从文本内容创建知识库文档。
     * 把文本内容切成小段，存入数据库和搜索引擎，方便后续检索。
     * @param title 文档标题
     * @param source 文档来源
     * @param tags 文档标签
     * @param content 文档的文本内容
     * @return 创建成功的文档信息
     */
    KnowledgeDocument createFromText(String title, String source, String tags, String content);

    /**
     * 从知识库中检索相关内容。
     * 根据问题在知识库中搜索最相关的内容片段。
     * @param query 用户的问题
     * @param topK 返回最相关的前几条内容
     * @return 相关内容的文本列表
     */
    List<String> retrieveContext(String query, int topK);
}


