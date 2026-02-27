package com.zhixue.interaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixue.interaction.domain.entity.CourseLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 课程点赞 Mapper。
 */
@Mapper
public interface CourseLikeMapper extends BaseMapper<CourseLike> {

    /**
     * 查询用户是否点赞过某课程（包含已删除记录）。
     */
    @Select("SELECT * FROM interaction_course_like WHERE user_id = #{userId} AND course_id = #{courseId} LIMIT 1")
    CourseLike selectByUserAndCourseIncludeDeleted(@Param("userId") Long userId, @Param("courseId") Long courseId);
}
