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

    PageResult<Course> pageCourses(PageQuery query, String title, Long categoryId, Integer status, Integer shelfStatus);

    java.util.List<java.util.Map<String, Object>> listBanners(int limit);

    java.util.List<Course> listHotCourses(int limit);

    java.util.List<Course> listLatestCourses(int limit);

    Course getById(Long id);

    boolean saveCourse(Course course);

    boolean updateCourse(Course course);

    boolean publish(Long id);

    boolean shelf(Long id, Integer shelfStatus);

    boolean remove(Long id);
}
