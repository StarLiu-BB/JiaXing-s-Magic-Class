package com.zhixue.course.controller;

import com.zhixue.api.course.RemoteCourseService;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.exception.GlobalExceptionHandler;
import com.zhixue.course.domain.entity.Course;
import com.zhixue.course.service.CourseSearchService;
import com.zhixue.course.service.CourseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 服务间契约测试：Feign 客户端声明的路径必须能命中课程服务的真实控制器。
 *
 * <p>源于一次真实故障：RemoteCourseService 写的是 /course/{id}，但 /course 只是网关前缀
 * （StripPrefix=1 后被剥掉），Feign 走服务直连不经网关，于是命中不存在的路径返回 500。
 * 该客户端写成后一直无人调用，直到订单服务接入核价才暴露，所有课程都被判为"不存在"。</p>
 *
 * <p>本测试直接读取 Feign 接口注解上的路径发请求，接口路径一旦与控制器错配即失败。</p>
 */
@ExtendWith(MockitoExtension.class)
class RemoteCourseContractTest {

    @Mock
    private CourseService courseService;

    @Mock
    private CourseSearchService courseSearchService;

    @Mock
    private com.zhixue.course.service.support.LessonAccessPolicy lessonAccessPolicy;

    @InjectMocks
    private CourseController courseController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(courseController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String feignPath(String method, Class<?>... params) throws Exception {
        return RemoteCourseService.class.getMethod(method, params).getAnnotation(GetMapping.class).value()[0];
    }

    @Test
    void getCoursePathMustHitControllerAndExposePricingFields() throws Exception {
        Course course = new Course();
        course.setId(2004L);
        course.setTitle("AI 助教工作流基础");
        course.setPrice(new BigDecimal("99.00"));
        course.setStatus(1);
        course.setShelfStatus(1);
        given(courseService.getById(2004L)).willReturn(course);

        String path = feignPath("getCourse", Long.class).replace("{courseId}", "2004");

        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                // CourseInfo 按这些字段名反序列化，改名会让订单核价静默拿到 null
                .andExpect(jsonPath("$.data.id").value(2004))
                .andExpect(jsonPath("$.data.title").value("AI 助教工作流基础"))
                .andExpect(jsonPath("$.data.price").value(99.00))
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.shelfStatus").value(1));
    }

    @Test
    void listCoursesPathMustHitController() throws Exception {
        given(courseService.pageCourses(org.mockito.ArgumentMatchers.argThat(query ->
                Integer.valueOf(1).equals(query.status()) && Integer.valueOf(1).equals(query.shelfStatus())), any()))
                .willReturn(PageResult.of(List.of(), 0L, 10));

        String path = feignPath("listCourses", com.zhixue.common.core.domain.PageQuery.class);
        assertThat(path).doesNotStartWith("/course/");

        mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
