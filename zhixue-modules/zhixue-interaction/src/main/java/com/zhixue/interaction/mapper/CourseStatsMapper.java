package com.zhixue.interaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixue.interaction.domain.entity.CourseStats;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 课程统计 Mapper。
 */
@Mapper
public interface CourseStatsMapper extends BaseMapper<CourseStats> {

    /**
     * 增加点赞数。
     */
    @Update("UPDATE interaction_course_stats SET like_count = like_count + #{delta}, update_time = NOW() WHERE course_id = #{courseId} AND deleted = 0")
    int incrementLikeCount(@Param("courseId") Long courseId, @Param("delta") int delta);

    /**
     * 增加收藏数。
     */
    @Update("UPDATE interaction_course_stats SET favorite_count = favorite_count + #{delta}, update_time = NOW() WHERE course_id = #{courseId} AND deleted = 0")
    int incrementFavoriteCount(@Param("courseId") Long courseId, @Param("delta") int delta);
}
