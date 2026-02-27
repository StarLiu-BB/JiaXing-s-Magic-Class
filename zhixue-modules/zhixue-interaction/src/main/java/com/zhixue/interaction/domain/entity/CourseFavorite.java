package com.zhixue.interaction.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixue.common.mybatis.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 课程收藏实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("interaction_course_favorite")
public class CourseFavorite extends BaseEntity {

    /**
     * 用户ID。
     */
    private Long userId;

    /**
     * 课程ID。
     */
    private Long courseId;
}
