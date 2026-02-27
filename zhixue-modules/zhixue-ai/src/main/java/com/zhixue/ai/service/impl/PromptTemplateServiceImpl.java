package com.zhixue.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixue.ai.domain.entity.PromptTemplate;
import com.zhixue.ai.mapper.PromptTemplateMapper;
import com.zhixue.ai.service.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 提示词模板服务的实现类。
 * 这个类实现了提示词模板的管理功能，包括查询、保存等。
 */
@Service
@RequiredArgsConstructor
public class PromptTemplateServiceImpl implements PromptTemplateService {

    private final PromptTemplateMapper promptTemplateMapper;

    /**
     * 根据模板编号查询模板。
     * @param code 模板编号，比如 COURSE_QA
     * @return 模板信息，如果不存在返回 null
     */
    @Override
    public PromptTemplate getByCode(String code) {
        LambdaQueryWrapper<PromptTemplate> qw = new LambdaQueryWrapper<>();
        qw.eq(PromptTemplate::getCode, code);
        return promptTemplateMapper.selectOne(qw);
    }

    /**
     * 查询所有模板。
     * @return 所有模板的列表
     */
    @Override
    public List<PromptTemplate> listAll() {
        return promptTemplateMapper.selectList(null);
    }

    /**
     * 保存或更新模板。
     * 如果模板已存在则更新，不存在则新建。
     * @param template 模板信息
     * @return 保存后的模板信息
     */
    @Override
    public PromptTemplate save(PromptTemplate template) {
        if (template.getId() == null) {
            // 新建模板
            promptTemplateMapper.insert(template);
        } else {
            // 更新已有模板
            promptTemplateMapper.updateById(template);
        }
        return template;
    }
}


