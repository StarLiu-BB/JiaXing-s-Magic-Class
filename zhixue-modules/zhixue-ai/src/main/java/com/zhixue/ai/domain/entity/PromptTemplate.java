package com.zhixue.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixue.common.mybatis.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 提示词模板实体类。
 * 这个类对应数据库中的提示词模板表。
 * 提示词模板就是给 AI 用的固定格式，比如课程答疑的模板。
 * 使用模板可以统一回答风格，不用每次都重新写提示词。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("prompt_template")
public class PromptTemplate extends BaseEntity {

    /**
     * 模板编号，唯一标识一个模板，比如 COURSE_QA 表示课程答疑模板
     */
    private String code;

    /**
     * 模板名称，比如"课程答疑模板"
     */
    private String name;

    /**
     * 模板内容，就是给 AI 的提示词，可以用 {{变量名}} 表示占位符
     */
    private String content;

    /**
     * 模板说明，描述这个模板的用途
     */
    private String description;
}


