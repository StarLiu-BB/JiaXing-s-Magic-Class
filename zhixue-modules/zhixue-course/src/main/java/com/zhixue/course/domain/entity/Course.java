package com.zhixue.course.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixue.common.mybatis.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 课程实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("course")
public class Course extends BaseEntity {

    private String title;
    private String description;
    private Long teacherId;
    private Long categoryId;
    private String coverUrl;
    private BigDecimal price;
    private Integer status; // 0草稿 1已发布
    private Integer shelfStatus; // 0下架 1上架
    private LocalDateTime publishTime;
}

