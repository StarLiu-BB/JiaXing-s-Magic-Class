package com.zhixue.course.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixue.common.mybatis.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 小节实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("section")
public class Section extends BaseEntity {

    private Long courseId;
    private Long chapterId;
    private String title;
    private String videoUrl;
    private Integer duration; // 秒
    private Integer orderNum;
    private Integer status; // 0未发布 1已发布
}

