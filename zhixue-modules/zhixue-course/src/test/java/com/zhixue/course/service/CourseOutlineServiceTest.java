package com.zhixue.course.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixue.api.course.domain.CourseOutline;
import com.zhixue.api.course.domain.CourseOutlineQuery;
import com.zhixue.api.course.domain.LessonOutline;
import com.zhixue.course.domain.entity.Chapter;
import com.zhixue.course.domain.entity.Course;
import com.zhixue.course.domain.entity.Section;
import com.zhixue.course.mapper.ChapterMapper;
import com.zhixue.course.mapper.CourseMapper;
import com.zhixue.course.mapper.SectionMapper;
import com.zhixue.course.service.impl.CourseOutlineServiceImpl;
import com.zhixue.course.service.support.LessonAccessPolicy;
import com.zhixue.course.service.support.LessonAccessPolicy.Viewer;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 推荐卡片的数据源：按请求体里的学生身份判定可播放；只给学生买得到的课；
 * 顺序与请求一致（推荐按相关度排序，这里不能打乱）。
 */
class CourseOutlineServiceTest {

    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Course.class);
        TableInfoHelper.initTableInfo(assistant, Chapter.class);
        TableInfoHelper.initTableInfo(assistant, Section.class);
    }

    private final CourseMapper courseMapper = mock(CourseMapper.class);
    private final ChapterMapper chapterMapper = mock(ChapterMapper.class);
    private final SectionMapper sectionMapper = mock(SectionMapper.class);
    private final LessonAccessPolicy policy = mock(LessonAccessPolicy.class);
    private final CourseOutlineServiceImpl service =
            new CourseOutlineServiceImpl(courseMapper, chapterMapper, sectionMapper, policy);

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void keepsRequestOrderAndOnlyQueriesPublishedCoursesOnShelf() {
        when(courseMapper.selectList(any())).thenReturn(List.of(course(2006L), course(2007L)));
        when(chapterMapper.selectList(any())).thenReturn(List.of());
        when(sectionMapper.selectList(any())).thenReturn(List.of());
        when(policy.mask(any(Course.class), anyCollection(), any(Viewer.class))).thenReturn(false);

        List<CourseOutline> outlines = service.outlines(new CourseOutlineQuery(1003L, false, List.of(2007L, 2002L, 2006L)));

        // 2002 下架：查询条件把它排除了，数据库不返回，这里也不出现
        assertThat(outlines).extracting(CourseOutline::getCourseId).containsExactly(2007L, 2006L);
        ArgumentCaptor<Wrapper> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(courseMapper).selectList(query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("shelf_status").contains("status");
    }

    @Test
    void numbersLessonsByChapterOrderAndUsesExplicitViewer() {
        when(courseMapper.selectList(any())).thenReturn(List.of(course(2007L)));
        when(chapterMapper.selectList(any())).thenReturn(List.of(chapter(3017L, 2), chapter(3016L, 1)));
        when(sectionMapper.selectList(any())).thenReturn(List.of(
                section(4024L, 3017L, 2), section(4021L, 3016L, 1), section(4023L, 3017L, 1), section(4022L, 3016L, 2)));
        Viewer student = new Viewer(1003L, false);
        // 未购买：试看前 2 节（4021、4022）可播放，其余锁定
        when(policy.mask(any(Course.class), anyCollection(), any(Viewer.class))).thenAnswer(invocation -> {
            Collection<Section> lessons = invocation.getArgument(1);
            lessons.forEach(l -> {
                boolean preview = Set.of(4021L, 4022L).contains(l.getId());
                l.setPreview(preview);
                l.setLocked(!preview);
            });
            return false;
        });

        CourseOutline outline = service.outlines(new CourseOutlineQuery(1003L, false, List.of(2007L))).get(0);

        assertThat(outline.getLessons()).extracting(LessonOutline::getLessonId)
                .containsExactly(4021L, 4022L, 4023L, 4024L);
        assertThat(outline.getLessons()).extracting(l -> l.getChapterIndex() + "." + l.getLessonIndex())
                .containsExactly("1.1", "1.2", "2.1", "2.2");
        assertThat(outline.getLessons()).extracting(LessonOutline::isPlayable)
                .containsExactly(true, true, false, false);
        assertThat(outline.isCanStudyAll()).isFalse();
        assertThat(outline.isPreviewable()).isTrue();
        assertThat(outline.getPrice()).isEqualByComparingTo("99.00");
        ArgumentCaptor<Viewer> viewer = ArgumentCaptor.forClass(Viewer.class);
        verify(policy).mask(any(Course.class), anyCollection(), viewer.capture());
        assertThat(viewer.getValue()).isEqualTo(student);
    }

    @Test
    void emptyRequestDoesNotTouchDatabase() {
        assertThat(service.outlines(new CourseOutlineQuery(1003L, false, List.of()))).isEmpty();
        verify(courseMapper, never()).selectList(any());
    }

    private static Course course(long id) {
        Course course = new Course();
        course.setId(id);
        course.setTitle("课程 " + id);
        course.setTeacherId(1002L);
        course.setCoverUrl("https://dummyimage.com/600x340.png");
        course.setPrice(new BigDecimal("99.00"));
        course.setAllowPreview(1);
        course.setPreviewLessonCount(2);
        course.setStatus(1);
        course.setShelfStatus(1);
        return course;
    }

    private static Chapter chapter(long id, int order) {
        Chapter chapter = new Chapter();
        chapter.setId(id);
        chapter.setCourseId(2007L);
        chapter.setOrderNum(order);
        return chapter;
    }

    private static Section section(long id, long chapterId, int order) {
        Section section = new Section();
        section.setId(id);
        section.setCourseId(2007L);
        section.setChapterId(chapterId);
        section.setTitle("课时 " + id);
        section.setDuration(900);
        section.setOrderNum(order);
        section.setVideoUrl("https://example.com/" + id + ".mp4");
        return section;
    }
}
