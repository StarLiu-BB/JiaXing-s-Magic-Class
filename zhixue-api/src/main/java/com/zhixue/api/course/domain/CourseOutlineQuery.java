package com.zhixue.api.course.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 查询课程大纲：按谁的身份判定可播放，以及要哪几门课 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseOutlineQuery {

    /** 学生 ID；为空按匿名判定 */
    private Long userId;
    /** 是否管理员（可观看全部课时） */
    private boolean admin;
    private List<Long> courseIds;
}
