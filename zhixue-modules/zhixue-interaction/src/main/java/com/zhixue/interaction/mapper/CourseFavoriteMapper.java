package com.zhixue.interaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixue.interaction.domain.entity.CourseFavorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 课程收藏 Mapper。
 */
@Mapper
public interface CourseFavoriteMapper extends BaseMapper<CourseFavorite> {

    /**
     * 查询用户是否收藏过某课程（包含已删除记录）。
     */
    @Select("SELECT * FROM interaction_course_favorite WHERE user_id = #{userId} AND course_id = #{courseId} LIMIT 1")
    CourseFavorite selectByUserAndCourseIncludeDeleted(@Param("userId") Long userId, @Param("courseId") Long courseId);
}
