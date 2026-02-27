package com.zhixue.course.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixue.common.mybatis.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 章节实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chapter")
public class Chapter extends BaseEntity {

    private Long courseId;
    private String title;
    private Integer orderNum;
}

