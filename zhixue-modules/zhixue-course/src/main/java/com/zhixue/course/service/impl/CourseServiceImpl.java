package com.zhixue.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixue.common.core.domain.PageQuery;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.course.domain.entity.Chapter;
import com.zhixue.course.domain.entity.Course;
import com.zhixue.course.domain.entity.Section;
import com.zhixue.course.mapper.ChapterMapper;
import com.zhixue.course.mapper.CourseMapper;
import com.zhixue.course.mapper.SectionMapper;
import com.zhixue.course.service.CourseService;
import com.zhixue.course.service.support.CourseAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 课程服务实现。
 */
@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private final CourseMapper courseMapper;
    private final ChapterMapper chapterMapper;
    private final SectionMapper sectionMapper;
    private final CourseAccessGuard courseAccessGuard;

    @Override
    public PageResult<Course> pageCourses(PageQuery query, String title, Long categoryId, Integer status, Integer shelfStatus) {
        LambdaQueryWrapper<Course> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(title)) {
            qw.like(Course::getTitle, title.trim());
        }
        if (categoryId != null) {
            qw.eq(Course::getCategoryId, categoryId);
        }
        if (status != null) {
            qw.eq(Course::getStatus, status);
        }
        if (shelfStatus != null) {
            qw.eq(Course::getShelfStatus, shelfStatus);
        }
        if (courseAccessGuard.isTeacherScoped()) {
            qw.eq(Course::getTeacherId, courseAccessGuard.currentTeacherId());
        }
        qw.orderByDesc(Course::getCreateTime);
        Page<Course> page = courseMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), qw);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getSize());
    }

    @Override
    public List<Map<String, Object>> listBanners(int limit) {
        return listPublicCourses(limit).stream()
                .filter(course -> StringUtils.hasText(course.getCoverUrl()))
                .map(course -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", course.getId());
                    item.put("title", course.getTitle());
                    item.put("imageUrl", course.getCoverUrl());
                    item.put("linkType", "course");
                    item.put("linkId", course.getId());
                    return item;
                })
                .toList();
    }

    @Override
    public List<Course> listHotCourses(int limit) {
        return listPublicCourses(limit);
    }

    @Override
    public List<Course> listLatestCourses(int limit) {
        LambdaQueryWrapper<Course> queryWrapper = publicCourseQuery()
                .orderByDesc(Course::getPublishTime)
                .orderByDesc(Course::getCreateTime)
                .last("limit " + Math.max(limit, 1));
        return courseMapper.selectList(queryWrapper);
    }

    @Override
    public Course getById(Long id) {
        return courseAccessGuard.requireReadableCourse(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveCourse(Course course) {
        course.setStatus(0);
        course.setShelfStatus(0);
        applyCourseDefaults(course);
        if (courseAccessGuard.isTeacherScoped()) {
            course.setTeacherId(courseAccessGuard.currentTeacherId());
        }
        return courseMapper.insert(course) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateCourse(Course course) {
        Course existing = courseAccessGuard.requireWritableCourse(course.getId());
        if (courseAccessGuard.isTeacherScoped()) {
            course.setTeacherId(existing.getTeacherId());
        } else if (course.getTeacherId() == null) {
            course.setTeacherId(existing.getTeacherId());
        }
        applyCourseDefaults(course, existing);
        return courseMapper.updateById(course) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean publish(Long id) {
        Course course = courseAccessGuard.requireWritableCourse(id);
        course.setStatus(1);
        course.setShelfStatus(1);
        course.setPublishTime(LocalDateTime.now());
        return courseMapper.updateById(course) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean shelf(Long id, Integer shelfStatus) {
        Course course = courseAccessGuard.requireWritableCourse(id);
        course.setShelfStatus(shelfStatus);
        return courseMapper.updateById(course) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean remove(Long id) {
        courseAccessGuard.requireWritableCourse(id);
        LambdaQueryWrapper<Chapter> chapterWrapper = new LambdaQueryWrapper<>();
        chapterWrapper.eq(Chapter::getCourseId, id);
        java.util.List<Chapter> chapters = chapterMapper.selectList(chapterWrapper);
        if (!chapters.isEmpty()) {
            LambdaQueryWrapper<Section> sectionWrapper = new LambdaQueryWrapper<>();
            sectionWrapper.in(Section::getChapterId, chapters.stream().map(Chapter::getId).toList());
            sectionMapper.delete(sectionWrapper);
        }
        chapterMapper.delete(chapterWrapper);
        return courseMapper.deleteById(id) > 0;
    }

    private List<Course> listPublicCourses(int limit) {
        LambdaQueryWrapper<Course> queryWrapper = publicCourseQuery()
                .orderByDesc(Course::getPublishTime)
                .orderByDesc(Course::getUpdateTime)
                .last("limit " + Math.max(limit, 1));
        return courseMapper.selectList(queryWrapper);
    }

    private LambdaQueryWrapper<Course> publicCourseQuery() {
        LambdaQueryWrapper<Course> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Course::getStatus, 1)
                .eq(Course::getShelfStatus, 1);
        return queryWrapper;
    }

    private void applyCourseDefaults(Course course) {
        if (course.getOriginalPrice() == null) {
            course.setOriginalPrice(course.getPrice() == null ? BigDecimal.ZERO : course.getPrice());
        }
        if (course.getPackageType() == null) {
            course.setPackageType(1);
        }
        if (course.getAllowPreview() == null) {
            course.setAllowPreview(1);
        }
        if (course.getPreviewLessonCount() == null) {
            course.setPreviewLessonCount(1);
        }
        if (course.getValidityType() == null) {
            course.setValidityType(1);
        }
    }

    private void applyCourseDefaults(Course target, Course source) {
        if (target.getOriginalPrice() == null) {
            target.setOriginalPrice(source.getOriginalPrice());
        }
        if (target.getPackageType() == null) {
            target.setPackageType(source.getPackageType());
        }
        if (target.getAllowPreview() == null) {
            target.setAllowPreview(source.getAllowPreview());
        }
        if (target.getPreviewLessonCount() == null) {
            target.setPreviewLessonCount(source.getPreviewLessonCount());
        }
        if (target.getValidityType() == null) {
            target.setValidityType(source.getValidityType());
        }
    }
}
