package com.zhixue.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixue.ai.domain.entity.KnowledgeChunk;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库分片的数据访问接口。
 * 这个接口用来操作数据库中的知识库分片表，提供增删改查功能。
 * 继承自数据库操作工具，自动拥有常用的数据库操作方法。
 */
@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {
}


