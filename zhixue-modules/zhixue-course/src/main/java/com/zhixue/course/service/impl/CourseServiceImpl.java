package com.zhixue.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixue.common.core.domain.PageQuery;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.course.domain.entity.Course;
import com.zhixue.course.mapper.CourseMapper;
import com.zhixue.course.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 课程服务实现。
 */
@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private final CourseMapper courseMapper;

    @Override
    public PageResult<Course> pageCourses(PageQuery query, Integer status, Integer shelfStatus) {
        LambdaQueryWrapper<Course> qw = new LambdaQueryWrapper<>();
        if (status != null) {
            qw.eq(Course::getStatus, status);
        }
        if (shelfStatus != null) {
            qw.eq(Course::getShelfStatus, shelfStatus);
        }
        Page<Course> page = courseMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), qw);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getSize());
    }

    @Override
    public Course getById(Long id) {
        return courseMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveCourse(Course course) {
        course.setStatus(0);
        course.setShelfStatus(0);
        return courseMapper.insert(course) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateCourse(Course course) {
        return courseMapper.updateById(course) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean publish(Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            return false;
        }
        course.setStatus(1);
        course.setPublishTime(LocalDateTime.now());
        return courseMapper.updateById(course) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean shelf(Long id, Integer shelfStatus) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            return false;
        }
        course.setShelfStatus(shelfStatus);
        return courseMapper.updateById(course) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        return courseMapper.deleteById(id) > 0;
    }
}

