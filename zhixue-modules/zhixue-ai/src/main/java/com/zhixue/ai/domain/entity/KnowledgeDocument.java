package com.zhixue.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixue.common.mybatis.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库文档实体类。
 * 这个类对应数据库中的知识库文档表，存储上传到知识库的文档信息。
 * 比如课程资料、帮助文档等，这些文档会被切成小段供 AI 查询使用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kb_document")
public class KnowledgeDocument extends BaseEntity {

    /**
     * 文档标题
     */
    private String title;

    /**
     * 文档来源，比如 manual 表示手动上传，course 表示来自课程
     */
    private String source;

    /**
     * 文档标签，用于分类和检索，多个标签用逗号分隔
     */
    private String tags;

    /**
     * 文档状态，0 表示禁用，1 表示启用
     */
    private Integer status;
}


