package com.zhixue.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixue.api.course.domain.CourseOutline;
import com.zhixue.api.course.domain.CourseOutlineQuery;
import com.zhixue.api.course.domain.LessonOutline;
import com.zhixue.course.domain.entity.Chapter;
import com.zhixue.course.domain.entity.Course;
import com.zhixue.course.domain.entity.Section;
import com.zhixue.course.mapper.ChapterMapper;
import com.zhixue.course.mapper.CourseMapper;
import com.zhixue.course.mapper.SectionMapper;
import com.zhixue.course.service.CourseOutlineService;
import com.zhixue.course.service.support.LessonAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 组装课程大纲。观看权限完全交给 {@link LessonAccessPolicy}（试看与购买规则只维护一份），
 * 这里只负责查询、排序与编号。
 */
@Service
@RequiredArgsConstructor
public class CourseOutlineServiceImpl implements CourseOutlineService {

    private static final Comparator<Chapter> CHAPTER_ORDER = Comparator
            .comparing((Chapter c) -> c.getOrderNum() == null ? Integer.MAX_VALUE : c.getOrderNum())
            .thenComparing(Chapter::getId);
    private static final Comparator<Section> LESSON_ORDER = Comparator
            .comparing((Section s) -> s.getOrderNum() == null ? Integer.MAX_VALUE : s.getOrderNum())
            .thenComparing(Section::getId);

    private final CourseMapper courseMapper;
    private final ChapterMapper chapterMapper;
    private final SectionMapper sectionMapper;
    private final LessonAccessPolicy lessonAccessPolicy;

    @Override
    public List<CourseOutline> outlines(CourseOutlineQuery query) {
        List<Long> ids = query == null || query.getCourseIds() == null ? List.of() : query.getCourseIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .limit(MAX_COURSES)
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Course> courses = courseMapper.selectList(new LambdaQueryWrapper<Course>()
                        .in(Course::getId, ids)
                        // 只给学生买得到的课：已发布且上架（逻辑删除由 @TableLogic 自动排除）
                        .eq(Course::getStatus, 1)
                        .eq(Course::getShelfStatus, 1))
                .stream()
                .collect(Collectors.toMap(Course::getId, Function.identity()));
        if (courses.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Chapter>> chaptersByCourse = chapterMapper.selectList(new LambdaQueryWrapper<Chapter>()
                        .in(Chapter::getCourseId, courses.keySet()))
                .stream()
                .collect(Collectors.groupingBy(Chapter::getCourseId));
        Map<Long, List<Section>> lessonsByCourse = sectionMapper.selectList(new LambdaQueryWrapper<Section>()
                        .in(Section::getCourseId, courses.keySet()))
                .stream()
                .collect(Collectors.groupingBy(Section::getCourseId));

        LessonAccessPolicy.Viewer viewer = new LessonAccessPolicy.Viewer(query.getUserId(), query.isAdmin());
        List<CourseOutline> result = new ArrayList<>();
        for (Long id : ids) {
            Course course = courses.get(id);
            if (course == null) {
                continue;
            }
            List<Section> lessons = new ArrayList<>(lessonsByCourse.getOrDefault(id, List.of()));
            boolean full = lessonAccessPolicy.mask(course, lessons, viewer);
            result.add(toOutline(course, chaptersByCourse.getOrDefault(id, List.of()), lessons, full));
        }
        return result;
    }

    private CourseOutline toOutline(Course course, List<Chapter> chapters, List<Section> lessons, boolean full) {
        Map<Long, List<Section>> byChapter = lessons.stream().collect(Collectors.groupingBy(Section::getChapterId));
        List<Chapter> orderedChapters = chapters.stream().sorted(CHAPTER_ORDER).toList();
        List<LessonOutline> outlineLessons = new ArrayList<>();
        for (int c = 0; c < orderedChapters.size(); c++) {
            Chapter chapter = orderedChapters.get(c);
            List<Section> chapterLessons = byChapter.getOrDefault(chapter.getId(), List.of()).stream()
                    .sorted(LESSON_ORDER).toList();
            for (int l = 0; l < chapterLessons.size(); l++) {
                Section lesson = chapterLessons.get(l);
                LessonOutline item = new LessonOutline();
                item.setLessonId(lesson.getId());
                item.setChapterId(chapter.getId());
                item.setTitle(lesson.getTitle());
                item.setDuration(lesson.getDuration());
                item.setChapterIndex(c + 1);
                item.setLessonIndex(l + 1);
                item.setPlayable(!Boolean.TRUE.equals(lesson.getLocked()));
                outlineLessons.add(item);
            }
        }
        CourseOutline outline = new CourseOutline();
        outline.setCourseId(course.getId());
        outline.setTitle(course.getTitle());
        outline.setCoverUrl(course.getCoverUrl());
        outline.setPrice(course.getPrice());
        outline.setCanStudyAll(full);
        outline.setPreviewable(!full && lessons.stream().anyMatch(s -> Boolean.TRUE.equals(s.getPreview())));
        outline.setLessons(outlineLessons);
        return outline;
    }
}
