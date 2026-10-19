package com.zhixue.interaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixue.interaction.domain.entity.CourseFavorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    /**
     * 按当前状态条件改写逻辑删除标记，返回受影响行数（0 表示状态已被其他请求改过）。
     *
     * <p>不能用 updateById：deleted 是 @TableLogic 字段，MyBatis-Plus 既不会 SET 它，
     * 又会附加 WHERE deleted=0——取消从未落库，恢复也匹配不到任何行。</p>
     */
    @Update("UPDATE interaction_course_favorite SET deleted = #{to}, update_time = NOW() WHERE id = #{id} AND deleted = #{from}")
    int updateDeletedFlag(@Param("id") Long id, @Param("from") int from, @Param("to") int to);
}
