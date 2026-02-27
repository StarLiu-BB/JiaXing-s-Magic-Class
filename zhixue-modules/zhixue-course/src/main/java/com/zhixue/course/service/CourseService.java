package com.zhixue.course.service;

import com.zhixue.course.domain.entity.Course;
import com.zhixue.common.core.domain.PageQuery;
import com.zhixue.common.core.domain.PageResult;

/**
 * <p>
 * 课程服务。
 * </p>
 */
public interface CourseService {

    PageResult<Course> pageCourses(PageQuery query, Integer status, Integer shelfStatus);

    Course getById(Long id);

    boolean saveCourse(Course course);

    boolean updateCourse(Course course);

    boolean publish(Long id);

    boolean shelf(Long id, Integer shelfStatus);

    boolean remove(Long id);
}

