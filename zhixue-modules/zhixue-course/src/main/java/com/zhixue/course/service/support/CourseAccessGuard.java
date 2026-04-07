package com.zhixue.course.service.support;

import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.security.utils.SecurityUtils;
import com.zhixue.course.domain.entity.Chapter;
import com.zhixue.course.domain.entity.Course;
import com.zhixue.course.domain.entity.Section;
import com.zhixue.course.mapper.ChapterMapper;
import com.zhixue.course.mapper.CourseMapper;
import com.zhixue.course.mapper.SectionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 课程读写访问控制，约束教师只能管理自己的课程。
 */
@Component
@RequiredArgsConstructor
public class CourseAccessGuard {

    private final CourseMapper courseMapper;
    private final ChapterMapper chapterMapper;
    private final SectionMapper sectionMapper;

    public boolean isAdmin() {
        return SecurityUtils.hasAnyRole("ADMIN");
    }

    public boolean canManageCourses() {
        return SecurityUtils.hasAnyPermission("course:list", "course:edit", "course:publish", "course:delete", "course:offline");
    }

    public boolean isTeacherScoped() {
        return canManageCourses() && !isAdmin();
    }

    public Long currentTeacherId() {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new ServiceException(HttpStatus.UNAUTHORIZED, "未登录或令牌已失效");
        }
        return userId;
    }

    public Course requireReadableCourse(Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "课程不存在");
        }
        if (isPublicCourse(course)) {
            return course;
        }
        if (!canManageCourses()) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "课程不存在");
        }
        if (isAdmin() || Objects.equals(course.getTeacherId(), currentTeacherId())) {
            return course;
        }
        throw new ServiceException(HttpStatus.FORBIDDEN, "无权访问该课程");
    }

    public Course requireWritableCourse(Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "课程不存在");
        }
        if (isAdmin() || Objects.equals(course.getTeacherId(), currentTeacherId())) {
            return course;
        }
        throw new ServiceException(HttpStatus.FORBIDDEN, "无权操作该课程");
    }

    public Chapter requireReadableChapter(Long chapterId) {
        Chapter chapter = chapterMapper.selectById(chapterId);
        if (chapter == null) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "章节不存在");
        }
        requireReadableCourse(chapter.getCourseId());
        return chapter;
    }

    public Chapter requireWritableChapter(Long chapterId) {
        Chapter chapter = chapterMapper.selectById(chapterId);
        if (chapter == null) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "章节不存在");
        }
        requireWritableCourse(chapter.getCourseId());
        return chapter;
    }

    public Section requireReadableSection(Long sectionId) {
        Section section = sectionMapper.selectById(sectionId);
        if (section == null) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "课时不存在");
        }
        requireReadableCourse(section.getCourseId());
        return section;
    }

    public Section requireWritableSection(Long sectionId) {
        Section section = sectionMapper.selectById(sectionId);
        if (section == null) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "课时不存在");
        }
        requireWritableCourse(section.getCourseId());
        return section;
    }

    public boolean isPublicCourse(Course course) {
        return course.getStatus() != null
                && course.getStatus() == 1
                && course.getShelfStatus() != null
                && course.getShelfStatus() == 1;
    }
}
