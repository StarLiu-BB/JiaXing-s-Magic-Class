package com.zhixue.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixue.course.domain.entity.Course;
import org.apache.ibatis.annotations.Mapper;

/**
 * 课程表 Mapper。
 */
@Mapper
public interface CourseMapper extends BaseMapper<Course> {
}

