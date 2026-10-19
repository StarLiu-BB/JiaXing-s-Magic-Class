package com.zhixue.course.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.common.core.exception.GlobalExceptionHandler;
import com.zhixue.course.domain.entity.Chapter;
import com.zhixue.course.domain.entity.Course;
import com.zhixue.course.domain.entity.Section;
import com.zhixue.course.service.ChapterService;
import com.zhixue.course.service.CourseSearchService;
import com.zhixue.course.service.CourseService;
import com.zhixue.course.service.SectionService;
import com.zhixue.course.service.support.LessonAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 新增课程/章节/课时必须返回新记录（含 ID）。
 *
 * <p>修复前三个接口都返回 R&lt;Void&gt;。课程发布页靠返回的 ID 继续添加章节、课时：
 * 拿不到 ID 时 courseId 一直为空，章节保存被拦下（"请先保存课程基本信息"），
 * 再点保存又会新建一门重复课程（经网关实测 data 为 null）。</p>
 */
class CreateReturnsIdTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CourseService courseService = mock(CourseService.class);
    private final ChapterService chapterService = mock(ChapterService.class);
    private final SectionService sectionService = mock(SectionService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new CourseController(courseService, mock(CourseSearchService.class), chapterService, mock(LessonAccessPolicy.class)),
                        new ChapterController(chapterService),
                        new SectionController(sectionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createCourseReturnsCreatedCourseWithId() throws Exception {
        given(courseService.saveCourse(any(Course.class))).willAnswer(inv -> {
            inv.<Course>getArgument(0).setId(2112159649338724354L);
            return true;
        });

        mockMvc.perform(post("/").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("title", "Redis 缓存实战", "price", 99))))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(2112159649338724354L))
                .andExpect(jsonPath("$.data.title").value("Redis 缓存实战"));
    }

    @Test
    void createChapterReturnsCreatedChapterWithId() throws Exception {
        given(chapterService.saveChapter(any(Chapter.class))).willAnswer(inv -> {
            inv.<Chapter>getArgument(0).setId(31L);
            return true;
        });

        mockMvc.perform(post("/chapter").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":2001,\"title\":\"第一章\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(31));
    }

    @Test
    void createLessonReturnsCreatedLessonWithIdOnBothPaths() throws Exception {
        given(sectionService.saveSection(any(Section.class))).willAnswer(inv -> {
            inv.<Section>getArgument(0).setId(41L);
            return true;
        });

        for (String path : new String[]{"/lesson", "/section"}) {
            mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"chapterId\":31,\"title\":\"1.1 缓存穿透\"}"))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(41));
        }
    }
}
