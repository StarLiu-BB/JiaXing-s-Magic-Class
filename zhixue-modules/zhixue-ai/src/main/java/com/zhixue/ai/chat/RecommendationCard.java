package com.zhixue.ai.chat;

import com.zhixue.api.course.domain.CourseOutline;
import com.zhixue.api.course.domain.LessonOutline;

import java.math.BigDecimal;
import java.util.List;

/**
 * 推荐卡片（实时状态，不落库）。ID 一律是字符串：雪花 ID 超出 JS 的安全整数范围。
 *
 * @param purchased 可观看全部课时（已购、免费、管理员或本课教师）
 * @param lessons   最多 2 个最相关的课时；为空表示只推荐课程
 */
public record RecommendationCard(String courseId, String title, String coverUrl, BigDecimal price,
                                 boolean purchased, boolean previewable, List<LessonCard> lessons) {

    public record LessonCard(String lessonId, String chapterId, String title, Integer duration,
                             int chapterIndex, int lessonIndex, boolean playable) {

        static LessonCard of(LessonOutline lesson) {
            return new LessonCard(String.valueOf(lesson.getLessonId()), String.valueOf(lesson.getChapterId()),
                    lesson.getTitle(), lesson.getDuration(), lesson.getChapterIndex(), lesson.getLessonIndex(),
                    lesson.isPlayable());
        }
    }

    static RecommendationCard of(CourseOutline outline, List<LessonOutline> lessons) {
        return new RecommendationCard(String.valueOf(outline.getCourseId()), outline.getTitle(),
                outline.getCoverUrl(), outline.getPrice(), outline.isCanStudyAll(), outline.isPreviewable(),
                lessons.stream().map(LessonCard::of).toList());
    }
}
