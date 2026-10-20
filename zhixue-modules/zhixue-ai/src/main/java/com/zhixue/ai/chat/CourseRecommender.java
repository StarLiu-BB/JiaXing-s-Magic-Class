package com.zhixue.ai.chat;

import com.zhixue.ai.config.RecommendConfig;
import com.zhixue.ai.service.KnowledgeBaseService.RetrievedChunk;
import com.zhixue.api.course.RemoteCourseOutlineService;
import com.zhixue.api.course.domain.CourseOutline;
import com.zhixue.api.course.domain.CourseOutlineQuery;
import com.zhixue.api.course.domain.LessonOutline;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.security.config.SecurityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 课程推荐：复用回答的同一次检索结果，不额外调用模型，因此回答引用的资料与推荐的课程不会对不上。
 *
 * <p>选课：命中片段按课程分组，课程分 = 组内最高余弦，过门槛后按分数取前 3；
 * 是否上架、是否已购、能否播放一律以课程服务为准。选课时：问句向量与课时标题向量比余弦，取前 2。
 * 任何外部失败都只记日志、返回空结果，回答照常完成。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseRecommender {

    private final RemoteCourseOutlineService remoteCourseOutlineService;
    private final LessonTitleVectors lessonTitleVectors;
    private final RecommendConfig config;
    private final SecurityProperties securityProperties;

    public record Result(List<StoredRecommendation> stored, List<RecommendationCard> cards) {

        public static Result empty() {
            return new Result(List.of(), List.of());
        }
    }

    public Result recommend(List<RetrievedChunk> hits, List<Float> questionVector, Viewer viewer) {
        if (hits == null || hits.isEmpty()) {
            return Result.empty();
        }
        Map<Long, Double> courseScore = new HashMap<>();
        for (RetrievedChunk hit : hits) {
            if (hit.courseId() != null) {
                courseScore.merge(hit.courseId(), hit.cosine(), Math::max);
            }
        }
        List<Long> ranked = courseScore.entrySet().stream()
                .filter(e -> e.getValue() >= config.getCourseMinCos())
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(config.getMaxCourses())
                .map(Map.Entry::getKey)
                .toList();
        if (ranked.isEmpty()) {
            return Result.empty();
        }
        Map<Long, CourseOutline> outlines = fetchOutlines(ranked, viewer);
        List<StoredRecommendation> stored = new ArrayList<>();
        List<RecommendationCard> cards = new ArrayList<>();
        for (Long courseId : ranked) {
            CourseOutline outline = outlines.get(courseId);
            if (outline == null) {
                continue; // 未上架、已删除或查询失败
            }
            List<LessonOutline> lessons = bestLessons(outline, questionVector);
            stored.add(new StoredRecommendation(String.valueOf(courseId), round(courseScore.get(courseId)),
                    lessons.stream().map(l -> String.valueOf(l.getLessonId())).toList()));
            cards.add(RecommendationCard.of(outline, lessons));
        }
        return new Result(stored, cards);
    }

    /**
     * 历史消息里的推荐只存了 ID：按最新的价格、购买与上架状态重新生成卡片。
     * 所有消息共用一次批量查询；返回列表与输入等长，查询失败时每条都是空列表。
     */
    public List<List<RecommendationCard>> resolve(List<List<StoredRecommendation>> perMessage, Viewer viewer) {
        List<Long> ids = perMessage.stream()
                .flatMap(List::stream)
                .map(s -> Long.valueOf(s.courseId()))
                .distinct()
                .limit(20)
                .toList();
        Map<Long, CourseOutline> outlines = ids.isEmpty() ? Map.of() : fetchOutlines(ids, viewer);
        List<List<RecommendationCard>> result = new ArrayList<>();
        for (List<StoredRecommendation> stored : perMessage) {
            List<RecommendationCard> cards = new ArrayList<>();
            for (StoredRecommendation item : stored) {
                CourseOutline outline = outlines.get(Long.valueOf(item.courseId()));
                if (outline == null) {
                    continue;
                }
                Map<String, LessonOutline> byId = outline.getLessons().stream()
                        .collect(Collectors.toMap(l -> String.valueOf(l.getLessonId()), Function.identity(), (a, b) -> a));
                cards.add(RecommendationCard.of(outline,
                        item.lessonIds().stream().map(byId::get).filter(Objects::nonNull).toList()));
            }
            result.add(cards);
        }
        return result;
    }

    private List<LessonOutline> bestLessons(CourseOutline outline, List<Float> questionVector) {
        List<LessonOutline> lessons = outline.getLessons() == null ? List.of() : outline.getLessons();
        if (lessons.isEmpty() || questionVector == null || questionVector.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Float>> vectors;
        try {
            vectors = lessonTitleVectors.vectorsOf(lessons);
        } catch (RuntimeException e) {
            log.warn("课时标题向量化失败，只推荐课程不标课时 courseId={}", outline.getCourseId(), e);
            return List.of();
        }
        record Scored(LessonOutline lesson, double cos) {
        }
        return lessons.stream()
                .filter(l -> vectors.containsKey(l.getLessonId()))
                .map(l -> new Scored(l, cosine(questionVector, vectors.get(l.getLessonId()))))
                .filter(s -> s.cos() >= config.getLessonMinCos())
                .sorted(Comparator.comparingDouble(Scored::cos).reversed())
                .limit(config.getMaxLessons())
                .map(Scored::lesson)
                .toList();
    }

    private Map<Long, CourseOutline> fetchOutlines(List<Long> courseIds, Viewer viewer) {
        try {
            R<List<CourseOutline>> response = remoteCourseOutlineService.outlines(
                    new CourseOutlineQuery(viewer.userId(), viewer.admin(), courseIds),
                    "true", securityProperties.getInternalToken());
            if (response == null || !response.isSuccess() || response.getData() == null) {
                log.error("查询课程大纲失败，本次不推荐 courseIds={} msg={}", courseIds,
                        response == null ? null : response.getMsg());
                return Map.of();
            }
            return response.getData().stream()
                    .collect(Collectors.toMap(CourseOutline::getCourseId, Function.identity(), (a, b) -> a));
        } catch (RuntimeException e) {
            log.error("调用课程服务查询大纲异常，本次不推荐 courseIds={}", courseIds, e);
            return Map.of();
        }
    }

    /** 余弦相似度；维度不一致或含零向量时为 0 */
    static double cosine(List<Float> a, List<Float> b) {
        if (a == null || b == null || a.isEmpty() || a.size() != b.size()) {
            return 0;
        }
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            na += a.get(i) * a.get(i);
            nb += b.get(i) * b.get(i);
        }
        return na == 0 || nb == 0 ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
