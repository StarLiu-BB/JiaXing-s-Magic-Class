package com.zhixue.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixue.ai.domain.entity.KnowledgeChunk;
import com.zhixue.ai.domain.entity.KnowledgeDocument;
import com.zhixue.ai.mapper.KnowledgeChunkMapper;
import com.zhixue.ai.mapper.KnowledgeDocumentMapper;
import com.zhixue.ai.rag.TextSplitter;
import com.zhixue.ai.service.KnowledgeBaseService;
import com.zhixue.ai.service.VectorStoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库服务的实现类。
 * 这个类实现了知识库的管理功能，包括上传文档和检索内容。
 * 上传文档时会自动切成小段，并存入数据库和搜索引擎。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final TextSplitter textSplitter;
    private final VectorStoreService vectorStoreService;

    /**
     * 从文本内容创建知识库文档。
     * 先保存文档信息，然后把内容切成小段，每段都存入数据库和搜索引擎。
     * @param title 文档标题
     * @param source 文档来源
     * @param tags 文档标签
     * @param content 文档的文本内容
     * @return 创建成功的文档信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public KnowledgeDocument createFromText(String title, String source, String tags, String content) {
        // 保存文档信息
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setTitle(title);
        doc.setSource(source);
        doc.setTags(tags);
        doc.setStatus(1);
        documentMapper.insert(doc);

        // 把文档内容切成小段
        List<String> chunks = textSplitter.split(content);
        int index = 0;
        for (String chunkText : chunks) {
            // 保存分片信息
            KnowledgeChunk chunk = new KnowledgeChunk();
            chunk.setDocumentId(doc.getId());
            chunk.setChunkIndex(index++);
            chunk.setContent(chunkText);
            chunkMapper.insert(chunk);
            // 把分片存入搜索引擎
            String esId = vectorStoreService.indexChunk(chunk.getId(), chunkText);
            chunk.setEsId(esId);
            chunkMapper.updateById(chunk);
        }
        return doc;
    }

    /**
     * 从知识库中检索相关内容。
     * 根据问题在搜索引擎中找最相似的分片，然后从数据库取出分片内容。
     * @param query 用户的问题
     * @param topK 返回最相关的前几条内容
     * @return 相关内容的文本列表
     */
    @Override
    public List<String> retrieveContext(String query, int topK) {
        // 在搜索引擎中查找最相关的分片编号
        List<Long> chunkIds = vectorStoreService.search(query, topK);
        if (chunkIds.isEmpty()) {
            return List.of();
        }
        // 从数据库取出分片内容
        List<String> texts = new ArrayList<>();
        for (Long id : chunkIds) {
            KnowledgeChunk chunk = chunkMapper.selectById(id);
            if (chunk != null) {
                texts.add(chunk.getContent());
            }
        }
        return texts;
    }
}


