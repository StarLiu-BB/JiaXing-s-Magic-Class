package com.zhixue.course.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixue.common.core.domain.PageQuery;
import com.zhixue.course.domain.entity.Category;
import com.zhixue.course.domain.entity.Course;
import com.zhixue.course.mapper.CategoryMapper;
import com.zhixue.course.mapper.ChapterMapper;
import com.zhixue.course.mapper.CourseMapper;
import com.zhixue.course.mapper.SectionMapper;
import com.zhixue.course.service.impl.CourseServiceImpl;
import com.zhixue.course.service.support.CourseAccessGuard;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoursePageQueryTest {
    @Mock private CourseMapper courseMapper;
    @Mock private ChapterMapper chapterMapper;
    @Mock private SectionMapper sectionMapper;
    @Mock private CategoryMapper categoryMapper;
    @Mock private CourseAccessGuard courseAccessGuard;
    @InjectMocks private CourseServiceImpl service;

    @BeforeAll
    static void initTables() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Course.class);
        TableInfoHelper.initTableInfo(assistant, Category.class);
    }

    private Category category(long id, long parentId) {
        Category category = new Category();
        category.setId(id);
        category.setParentId(parentId);
        return category;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private LambdaQueryWrapper<Course> query() {
        ArgumentCaptor<LambdaQueryWrapper<Course>> captor = (ArgumentCaptor) ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(courseMapper).selectPage(any(), captor.capture());
        captor.getValue().getSqlSegment();
        return captor.getValue();
    }

    @Test
    void parentCategoryIncludesDescendantsAndStopsOnCycles() {
        org.mockito.Mockito.lenient().when(categoryMapper.selectList(any())).thenReturn(List.of(
                category(110L, 112L), category(111L, 110L), category(112L, 111L), category(200L, 0L)));
        when(courseMapper.selectPage(any(), any())).thenReturn(new Page<Course>(1, 10));
        service.pageCourses(new PageQuery(), null, 110L, 1, 1);
        LambdaQueryWrapper<Course> query = query();
        assertThat(query.getSqlSegment()).contains("category_id IN");
        assertThat(query.getParamNameValuePairs().values()).contains(110L, 111L, 112L).doesNotContain(200L);
    }

    @Test
    void priceAscendingRetainsTeacherScopeAndPagination() {
        when(courseAccessGuard.isTeacherScoped()).thenReturn(true);
        when(courseAccessGuard.currentTeacherId()).thenReturn(2112366523950714882L);
        when(courseMapper.selectPage(any(), any())).thenReturn(new Page<Course>(2, 10).setTotal(21));
        PageQuery page = new PageQuery();
        page.setPageNum(2);
        page.setPageSize(10);
        var result = service.pageCourses(new com.zhixue.course.domain.query.CourseQuery(" Java ", null, 1, 1, "price_asc", null), page);
        LambdaQueryWrapper<Course> query = query();
        assertThat(query.getSqlSegment()).contains("teacher_id =", "ORDER BY price ASC,create_time DESC,id DESC");
        assertThat(query.getParamNameValuePairs().values()).contains(2112366523950714882L, "%Java%");
        assertThat(result.getPages()).isEqualTo(3);
    }

    @Test
    void latestUsesPublishTimeAndUnknownSortCannotInjectSql() {
        when(courseMapper.selectPage(any(), any())).thenReturn(new Page<Course>(1, 10));
        service.pageCourses(new com.zhixue.course.domain.query.CourseQuery(null, null, 1, 1, "new", null), new PageQuery());
        assertThat(query().getSqlSegment()).contains("ORDER BY publish_time DESC,create_time DESC,id DESC");
    }

    @Test
    void unknownSortUsesStableDefault() {
        when(courseMapper.selectPage(any(), any())).thenReturn(new Page<Course>(1, 10));
        service.pageCourses(new com.zhixue.course.domain.query.CourseQuery(null, null, null, null, "price; DROP TABLE course", null), new PageQuery());
        assertThat(query().getSqlSegment()).endsWith("ORDER BY create_time DESC,id DESC").doesNotContain("DROP");
    }
}
