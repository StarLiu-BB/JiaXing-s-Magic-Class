package com.zhixue.api.course.domain;

import com.zhixue.common.mybatis.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

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

    // 以下字段名与课程服务 Course 实体保持一致，供订单服务服务端核价使用
    private String title;
    private BigDecimal price;
    /** 0下架 1上架 */
    private Integer shelfStatus;
}

