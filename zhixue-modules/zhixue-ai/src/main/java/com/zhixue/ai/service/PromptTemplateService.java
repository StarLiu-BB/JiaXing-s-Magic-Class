package com.zhixue.ai.service;

import com.zhixue.ai.domain.entity.PromptTemplate;

import java.util.List;

/**
 * 提示词模板服务接口。
 * 这个接口定义了提示词模板的管理方法，包括查询、保存等。
 * 提示词模板可以统一 AI 的回答风格，不用每次都重新写提示词。
 */
public interface PromptTemplateService {

    /**
     * 根据模板编号查询模板。
     * @param code 模板编号，比如 COURSE_QA
     * @return 模板信息，如果不存在返回 null
     */
    PromptTemplate getByCode(String code);

    /**
     * 查询所有模板。
     * @return 所有模板的列表
     */
    List<PromptTemplate> listAll();

    /**
     * 保存或更新模板。
     * 如果模板已存在则更新，不存在则新建。
     * @param template 模板信息
     * @return 保存后的模板信息
     */
    PromptTemplate save(PromptTemplate template);
}


