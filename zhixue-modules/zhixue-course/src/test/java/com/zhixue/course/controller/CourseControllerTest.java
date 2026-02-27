package com.zhixue.course.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.course.domain.entity.Course;
import com.zhixue.course.service.CourseSearchService;
import com.zhixue.course.service.CourseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CourseController 集成测试。
 */
@WebMvcTest(CourseController.class)
class CourseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CourseService courseService;

    @MockBean
    private CourseSearchService courseSearchService;

    @Autowired
    private ObjectMapper objectMapper;

    private Course course;

    @BeforeEach
    void setUp() {
        course = new Course();
        course.setId(1L);
        course.setName("Spring Cloud 课程");
        course.setPrice(99.99);
        course.setStatus(1);
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
    void shouldDeleteCourse() throws Exception {
        given(courseService.remove(1L)).willReturn(true);

        mockMvc.perform(delete("/{id}", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
