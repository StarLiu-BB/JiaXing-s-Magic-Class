package com.zhixue.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixue.ai.domain.entity.PromptTemplate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提示词模板的数据访问接口。
 * 这个接口用来操作数据库中的提示词模板表，提供增删改查功能。
 * 继承自数据库操作工具，自动拥有常用的数据库操作方法。
 */
@Mapper
public interface PromptTemplateMapper extends BaseMapper<PromptTemplate> {
}


