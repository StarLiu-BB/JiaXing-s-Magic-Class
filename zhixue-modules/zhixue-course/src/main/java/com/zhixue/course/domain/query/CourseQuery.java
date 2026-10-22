package com.zhixue.course.domain.query;

import java.util.List;

public record CourseQuery(String title, Long categoryId, Integer status, Integer shelfStatus,
                          String sort, List<Long> teacherIds) {
    public static final String SORT_LATEST = "new";
    public static final String SORT_PRICE_ASC = "price_asc";
    public static final String SORT_PRICE_DESC = "price_desc";
}
