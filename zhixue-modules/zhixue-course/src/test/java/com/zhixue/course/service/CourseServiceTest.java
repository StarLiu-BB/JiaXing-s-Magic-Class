package com.zhixue.course.service;

import com.zhixue.common.core.domain.PageQuery;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.course.domain.entity.Course;
import com.zhixue.course.mapper.ChapterMapper;
import com.zhixue.course.mapper.CourseMapper;
import com.zhixue.course.mapper.SectionMapper;
import com.zhixue.course.service.impl.CourseServiceImpl;
import com.zhixue.course.service.support.CourseAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CourseService 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock
    private CourseMapper courseMapper;

    @Mock
    private ChapterMapper chapterMapper;

    @Mock
    private SectionMapper sectionMapper;

    @Mock
    private CourseAccessGuard courseAccessGuard;

    @InjectMocks
    private CourseServiceImpl courseService;

    private Course course;

    @BeforeEach
    void setUp() {
        course = new Course();
        course.setId(1L);
        course.setName("Java 微服务实战");
        course.setStatus(0); // 草稿
        course.setShelfStatus(0); // 下架
        course.setPrice(BigDecimal.valueOf(99.9));
        course.setCreateTime(LocalDateTime.now());
        course.setUpdateTime(LocalDateTime.now());
    }

    @Test
    void shouldReturnPageResult_WhenPageCourses() {
        PageQuery query = new PageQuery();
        query.setPageNum(1);
        query.setPageSize(10);

        Page<Course> page = new Page<>(1, 10);
        page.setRecords(Collections.singletonList(course));
        page.setTotal(1);

        when(courseAccessGuard.isTeacherScoped()).thenReturn(false);
        when(courseMapper.selectPage(any(Page.class), any())).thenReturn(page);

        PageResult<Course> result = courseService.pageCourses(query, null, null, null, null);

        assertThat(result).isNotNull();
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getName()).isEqualTo("Java 微服务实战");
    }

    @Test
    void shouldReturnCourse_WhenGetById() {
        when(courseAccessGuard.requireReadableCourse(1L)).thenReturn(course);

        Course result = courseService.getById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void shouldReturnTrue_WhenSaveCourse() {
        when(courseAccessGuard.isTeacherScoped()).thenReturn(false);
        when(courseMapper.insert(any(Course.class))).thenReturn(1);

        boolean result = courseService.saveCourse(course);

        assertThat(result).isTrue();
        verify(courseMapper).insert(any(Course.class));
    }

    @Test
    void shouldApplyCoursePackageDefaultsWhenSave() {
        when(courseAccessGuard.isTeacherScoped()).thenReturn(false);
        when(courseMapper.insert(any(Course.class))).thenReturn(1);

        Course raw = new Course();
        raw.setId(2L);
        raw.setTitle("课程包默认值测试");
        raw.setPrice(BigDecimal.valueOf(88.8));

        boolean result = courseService.saveCourse(raw);

        assertThat(result).isTrue();
        assertThat(raw.getOriginalPrice()).isEqualByComparingTo(BigDecimal.valueOf(88.8));
        assertThat(raw.getPackageType()).isEqualTo(1);
        assertThat(raw.getAllowPreview()).isEqualTo(1);
        assertThat(raw.getPreviewLessonCount()).isEqualTo(1);
        assertThat(raw.getValidityType()).isEqualTo(1);
    }

    @Test
    void shouldReturnTrue_WhenUpdateCourse() {
        when(courseAccessGuard.requireWritableCourse(1L)).thenReturn(course);
        when(courseAccessGuard.isTeacherScoped()).thenReturn(false);
        when(courseMapper.updateById(any(Course.class))).thenReturn(1);

        boolean result = courseService.updateCourse(course);

        assertThat(result).isTrue();
        verify(courseMapper).updateById(any(Course.class));
    }

    @Test
    void shouldReturnTrue_WhenPublish() {
        when(courseAccessGuard.requireWritableCourse(1L)).thenReturn(course);
        when(courseMapper.updateById(any(Course.class))).thenReturn(1);

        boolean result = courseService.publish(1L);

        assertThat(result).isTrue();
        verify(courseMapper).updateById(any(Course.class));
    }

    @Test
    void shouldReturnTrue_WhenRemove() {
        when(courseAccessGuard.requireWritableCourse(1L)).thenReturn(course);
        when(chapterMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(courseMapper.deleteById(1L)).thenReturn(1);
        boolean result = courseService.remove(1L);
        assertThat(result).isTrue();
        verify(courseMapper).deleteById(1L);
    }
}
