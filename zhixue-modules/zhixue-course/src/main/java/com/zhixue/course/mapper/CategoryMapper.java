package com.zhixue.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixue.course.domain.entity.Category;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
