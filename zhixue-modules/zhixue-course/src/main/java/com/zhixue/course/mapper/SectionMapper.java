package com.zhixue.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixue.course.domain.entity.Section;
import org.apache.ibatis.annotations.Mapper;

/**
 * 小节表 Mapper。
 */
@Mapper
public interface SectionMapper extends BaseMapper<Section> {
}

