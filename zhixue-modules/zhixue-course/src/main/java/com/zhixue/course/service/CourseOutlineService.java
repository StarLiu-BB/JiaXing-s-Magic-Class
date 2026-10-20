package com.zhixue.course.service;

import com.zhixue.api.course.domain.CourseOutline;
import com.zhixue.api.course.domain.CourseOutlineQuery;

import java.util.List;

public interface CourseOutlineService {

    /** 一次最多查询的课程数：推荐最多 3 门，历史会话的卡片也不会更多 */
    int MAX_COURSES = 20;

    /**
     * 按请求中的身份组装课程大纲。只返回已发布且上架的课程，顺序与 courseIds 一致。
     */
    List<CourseOutline> outlines(CourseOutlineQuery query);
}
