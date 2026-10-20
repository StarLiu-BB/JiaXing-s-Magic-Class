package com.zhixue.api.course.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 推荐卡片所需的课程概况与课时列表（已按指定学生判定可播放） */
@Data
public class CourseOutline {

    private Long courseId;
    private String title;
    private String coverUrl;
    private BigDecimal price;
    /** 可观看全部课时：已购、免费、管理员或本课教师 */
    private boolean canStudyAll;
    /** 不能看全部、但有试看课时 */
    private boolean previewable;
    private List<LessonOutline> lessons = new ArrayList<>();
}
