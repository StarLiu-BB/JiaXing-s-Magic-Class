package com.zhixue.course.domain.doc;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 课程搜索文档。
 * </p>
 */
@Data
@Document(indexName = "course_index")
public class CourseDoc {

    @Id
    private Long id;
    private String title;
    private String description;
    private Long teacherId;
    private Long categoryId;
    private String coverUrl;
    private BigDecimal price;
    private Integer status;
    private Integer shelfStatus;
    private LocalDateTime publishTime;
}

