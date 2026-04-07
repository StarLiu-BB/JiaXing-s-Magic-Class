package com.zhixue.course.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.exception.GlobalExceptionHandler;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.course.domain.entity.Course;
import com.zhixue.course.service.CourseSearchService;
import com.zhixue.course.service.CourseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CourseControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private CourseService courseService;

    @Mock
    private CourseSearchService courseSearchService;

    @InjectMocks
    private CourseController courseController;

    private MockMvc mockMvc;
    private Course course;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(courseController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        course = new Course();
        course.setId(1L);
        course.setName("Spring Cloud 课程");
        course.setPrice(BigDecimal.valueOf(99.99));
        course.setStatus(1);
        course.setShelfStatus(1);
    }

    @Test
    void shouldReturnCourseInfo_WhenGetInfo() throws Exception {
        given(courseService.getById(1L)).willReturn(course);

        mockMvc.perform(get("/info/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.name").value("Spring Cloud 课程"));
    }

    @Test
    void shouldFilterPublicListWhenAnonymous() throws Exception {
        PageResult<Course> pageResult = PageResult.of(java.util.List.of(course), 1, 10);
        given(courseService.pageCourses(any(), any(), any(), any(), any())).willReturn(pageResult);

        mockMvc.perform(get("/list").param("pageNum", "1").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].name").value("Spring Cloud 课程"));

        verify(courseService).pageCourses(any(), any(), any(), eq(1), eq(1));
    }

    @Test
    void shouldRejectDraftDetailWhenAnonymous() throws Exception {
        given(courseService.getById(1L))
                .willThrow(new ServiceException(HttpStatus.NOT_FOUND, "课程不存在"));

        mockMvc.perform(get("/detail/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg").value("课程不存在"));
    }

    @Test
    void shouldReturnCourseInfo_WhenGetDetailAlias() throws Exception {
        given(courseService.getById(1L)).willReturn(course);

        mockMvc.perform(get("/detail/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(1L));
    }

    @Test
    void shouldCreateCourse_WhenPostValidData() throws Exception {
        given(courseService.saveCourse(any(Course.class))).willReturn(true);

        mockMvc.perform(post("/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(course)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldUpdateCourse_WhenPutValidData() throws Exception {
        given(courseService.updateCourse(any(Course.class))).willReturn(true);

        mockMvc.perform(put("/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(course)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldPublishCourse() throws Exception {
        given(courseService.publish(1L)).willReturn(true);

        mockMvc.perform(post("/publish/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldOfflineCourseByAlias() throws Exception {
        given(courseService.shelf(1L, 0)).willReturn(true);

        mockMvc.perform(put("/{id}/offline", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shouldDeleteCourse() throws Exception {
        given(courseService.remove(1L)).willReturn(true);

        mockMvc.perform(delete("/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
