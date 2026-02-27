package com.zhixue.api.course.domain;

import com.zhixue.common.mybatis.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 课程信息 DTO。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CourseInfo extends BaseEntity {

    private String courseName;
    private String teacherName;
    private String coverUrl;
    private String description;
    private Integer status;
    private Integer lessonCount;
}

