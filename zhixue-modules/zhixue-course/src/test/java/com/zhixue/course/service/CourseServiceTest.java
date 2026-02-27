package com.zhixue.course.service;

import com.zhixue.common.core.domain.PageQuery;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.course.domain.entity.Course;
import com.zhixue.course.mapper.CourseMapper;
import com.zhixue.course.service.impl.CourseServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.mybatisflex.core.paginate.Page;

import java.time.LocalDateTime;

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
        course.setCreateTime(LocalDateTime.now());
        course.setUpdateTime(LocalDateTime.now());
    }

    @Test
    void shouldReturnPageResult_WhenPageCourses() {
        // Arrange
        PageQuery query = new PageQuery();
        query.setPageNo(1);
        query.setPageSize(10);
        
        Page<Course> page = new Page<>();
        page.setRecords(java.util.Collections.singletonList(course));
        page.setTotalRow(1);

        when(courseMapper.paginate(any(Page.class), any())).thenReturn(page);

        // Act
        PageResult<Course> result = courseService.pageCourses(query, null, null);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getList()).hasSize(1);
        assertThat(result.getList().get(0).getName()).isEqualTo("Java 微服务实战");
    }

    @Test
    void shouldReturnCourse_WhenGetById() {
        // Arrange
        when(courseMapper.selectOneById(1L)).thenReturn(course);

        // Act
        Course result = courseService.getById(1L);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void shouldReturnTrue_WhenSaveCourse() {
        // Arrange
        when(courseMapper.insert(any(Course.class))).thenReturn(1);

        // Act
        boolean result = courseService.saveCourse(course);

        // Assert
        assertThat(result).isTrue();
        verify(courseMapper).insert(any(Course.class));
    }

    @Test
    void shouldReturnTrue_WhenUpdateCourse() {
        // Arrange
        when(courseMapper.update(any(Course.class))).thenReturn(1);

        // Act
        boolean result = courseService.updateCourse(course);

        // Assert
        assertThat(result).isTrue();
        verify(courseMapper).update(any(Course.class));
    }

    @Test
    void shouldReturnTrue_WhenPublish() {
        // Arrange
        Course updateCourse = new Course();
        updateCourse.setId(1L);
        updateCourse.setStatus(1); // 发布
        when(courseMapper.update(any(Course.class))).thenReturn(1);

        // Act
        boolean result = courseService.publish(1L);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    void shouldReturnTrue_WhenRemove() {
        // Arrange
        when(courseMapper.deleteById(1L)).thenReturn(1);

        // Act
        boolean result = courseService.remove(1L);

        // Assert
        assertThat(result).isTrue();
        verify(courseMapper).deleteById(1L);
    }
}
