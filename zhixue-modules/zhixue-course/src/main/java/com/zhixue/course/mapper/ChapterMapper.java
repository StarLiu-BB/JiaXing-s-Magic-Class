package com.zhixue.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixue.course.domain.entity.Chapter;
import org.apache.ibatis.annotations.Mapper;

/**
 * 章节表 Mapper。
 */
@Mapper
public interface ChapterMapper extends BaseMapper<Chapter> {
}

