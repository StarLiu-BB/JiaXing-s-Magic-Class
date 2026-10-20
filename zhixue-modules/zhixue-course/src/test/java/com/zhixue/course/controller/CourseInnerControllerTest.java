package com.zhixue.course.controller;

import com.zhixue.api.course.RemoteCourseOutlineService;
import com.zhixue.api.course.domain.CourseOutline;
import com.zhixue.api.course.domain.CourseOutlineQuery;
import com.zhixue.common.core.exception.GlobalExceptionHandler;
import com.zhixue.common.security.config.InternalAccessConstants;
import com.zhixue.course.service.CourseOutlineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 身份由请求体给出，所以必须严格限定为内部调用：否则任何人带上别人的 userId，
 * 就能查出别人买过哪些课。网关从不设置 X-Inner-Call，外部请求一律 403。
 */
class CourseInnerControllerTest {

    private static final String BODY = "{\"userId\":1003,\"admin\":false,\"courseIds\":[2007]}";

    private final CourseOutlineService service = mock(CourseOutlineService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CourseOutline outline = new CourseOutline();
        outline.setCourseId(2007L);
        when(service.outlines(any())).thenReturn(List.of(outline));
        mockMvc = MockMvcBuilders.standaloneSetup(new CourseInnerController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void externalRequestIsForbidden() throws Exception {
        mockMvc.perform(post("/inner/outlines").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(jsonPath("$.code").value(403));
        verify(service, never()).outlines(any());
    }

    /** Feign 声明的路径必须命中真实控制器（同类故障：RemoteCourseService 曾误带 /course 前缀，一直 500） */
    @Test
    void feignPathHitsControllerForTrustedInternalCall() throws Exception {
        String path = RemoteCourseOutlineService.class
                .getMethod("outlines", CourseOutlineQuery.class, String.class, String.class)
                .getAnnotation(PostMapping.class).value()[0];

        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .requestAttr(InternalAccessConstants.TRUSTED_REQUEST_ATTR, Boolean.TRUE)
                        .header(InternalAccessConstants.INNER_CALL_HEADER, "true"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].courseId").value(2007));
    }
}
