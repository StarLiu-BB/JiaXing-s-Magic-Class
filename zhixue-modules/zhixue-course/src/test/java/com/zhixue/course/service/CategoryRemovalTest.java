package com.zhixue.course.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.course.domain.entity.Category;
import com.zhixue.course.domain.entity.Course;
import com.zhixue.course.mapper.CategoryMapper;
import com.zhixue.course.mapper.CourseMapper;
import com.zhixue.course.service.impl.CategoryServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 分类删除保护：原实现直接删除，子分类成为孤儿，引用该分类的课程指向不存在的分类。
 */
@ExtendWith(MockitoExtension.class)
class CategoryRemovalTest {

    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Category.class);
        TableInfoHelper.initTableInfo(assistant, Course.class);
    }

    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private CourseMapper courseMapper;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    @Test
    void categoryWithChildrenMustNotBeDeleted() {
        when(categoryMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> categoryService.remove(100L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("子分类");
        verify(categoryMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void categoryUsedByCoursesMustNotBeDeleted() {
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(courseMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> categoryService.remove(210L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("2 门课程");
        verify(categoryMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void unusedLeafCategoryCanBeDeleted() {
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(courseMapper.selectCount(any())).thenReturn(0L);
        when(categoryMapper.deleteById(999L)).thenReturn(1);

        assertThat(categoryService.remove(999L)).isTrue();
    }
}
