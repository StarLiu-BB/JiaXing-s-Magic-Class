package com.zhixue.interaction.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixue.common.mybatis.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 课程互动统计实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("interaction_course_stats")
public class CourseStats extends BaseEntity {

    /**
     * 课程ID。
     */
    private Long courseId;

    /**
     * 点赞数。
     */
    private Integer likeCount;

    /**
     * 收藏数。
     */
    private Integer favoriteCount;
}
