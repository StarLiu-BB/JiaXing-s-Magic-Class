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
    private String subtitle;
    private String description;
    private Long teacherId;
    private Long categoryId;
    private String coverUrl;
    private String posterUrl;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private Integer packageType;
    private Integer allowPreview;
    private Integer previewLessonCount;
    private Integer validityType;
    private String materials;
    private String faq;
    private Integer status; // 0草稿 1已发布
    private Integer shelfStatus; // 0下架 1上架
    private LocalDateTime publishTime;

    public String getName() {
        return title;
    }

    public void setName(String name) {
        this.title = name;
    }

    public String getCover() {
        return coverUrl;
    }

    public void setCover(String cover) {
        this.coverUrl = cover;
    }
}
