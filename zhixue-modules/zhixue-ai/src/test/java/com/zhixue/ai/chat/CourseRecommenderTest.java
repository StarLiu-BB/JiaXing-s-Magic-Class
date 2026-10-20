package com.zhixue.ai.chat;

import com.zhixue.ai.config.RecommendConfig;
import com.zhixue.ai.service.EmbeddingService;
import com.zhixue.ai.service.KnowledgeBaseService.RetrievedChunk;
import com.zhixue.api.course.RemoteCourseOutlineService;
import com.zhixue.api.course.domain.CourseOutline;
import com.zhixue.api.course.domain.CourseOutlineQuery;
import com.zhixue.api.course.domain.LessonOutline;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.security.config.SecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 推荐：课程门槛 0.55、课时门槛 0.50（余弦）；最多 3 门、每门 2 个课时；
 * 课程服务失败只是「没有推荐」，不能抛异常连累回答。
 *
 * <p>课时向量用二维单位向量，问句向量为 [1, 0]，于是课时向量 [c, √(1−c²)] 与问句的余弦恰好是 c。</p>
 */
class CourseRecommenderTest {

    private static final String TOKEN = "unit-test-internal-token-0123456789";
    private static final Viewer STUDENT = new Viewer(1003L, false);
    private static final List<Float> QUESTION = List.of(1f, 0f);

    private final RemoteCourseOutlineService outlineService = mock(RemoteCourseOutlineService.class);
    private final EmbeddingService embeddingService = mock(EmbeddingService.class);
    private CourseRecommender recommender;

    @BeforeEach
    void setUp() {
        RecommendConfig config = new RecommendConfig();
        SecurityProperties security = new SecurityProperties();
        security.setInternalToken(TOKEN);
        recommender = new CourseRecommender(outlineService, new LessonTitleVectors(embeddingService, config),
                config, security);
    }

    @Test
    void ranksCoursesByBestHitAboveThresholdAndCapsAtThree() {
        when(outlineService.outlines(any(), any(), any())).thenReturn(R.ok(List.of(
                outline(2004L), outline(2006L), outline(2007L))));

        CourseRecommender.Result result = recommender.recommend(List.of(
                hit(2006L, 0.62), hit(2006L, 0.50), hit(2007L, 0.58), hit(2005L, 0.56),
                hit(2004L, 0.70), hit(2009L, 0.40), hit(null, 0.90)), QUESTION, STUDENT);

        ArgumentCaptor<CourseOutlineQuery> query = ArgumentCaptor.forClass(CourseOutlineQuery.class);
        verify(outlineService).outlines(query.capture(), eq("true"), eq(TOKEN));
        // 2009 低于门槛、通用资料（courseId=null）不参与；按最高分排序后只取 3 门
        assertThat(query.getValue().getCourseIds()).containsExactly(2004L, 2006L, 2007L);
        assertThat(query.getValue().getUserId()).isEqualTo(1003L);
        assertThat(query.getValue().isAdmin()).isFalse();
        assertThat(result.cards()).extracting(RecommendationCard::courseId).containsExactly("2004", "2006", "2007");
        assertThat(result.stored()).extracting(StoredRecommendation::score).containsExactly(0.7, 0.62, 0.58);
    }

    @Test
    void coursesMissingFromOutlinesAreDropped() {
        // 2002 有资料但已下架：大纲接口不返回它
        when(outlineService.outlines(any(), any(), any())).thenReturn(R.ok(List.of()));

        CourseRecommender.Result result = recommender.recommend(List.of(hit(2002L, 0.80)), QUESTION, STUDENT);

        assertThat(result.cards()).isEmpty();
        assertThat(result.stored()).isEmpty();
    }

    @Test
    void picksTopTwoLessonsAboveLessonThreshold() {
        when(outlineService.outlines(any(), any(), any())).thenReturn(R.ok(List.of(outline(2007L,
                lesson(4021L, "线程的创建与生命周期"), lesson(4022L, "线程安全问题的根源"),
                lesson(4023L, "synchronized 与 ReentrantLock"), lesson(4024L, "ConcurrentHashMap 原理")))));
        Map<String, Double> cosByTitle = Map.of(
                "线程的创建与生命周期", 0.30, "线程安全问题的根源", 0.61,
                "synchronized 与 ReentrantLock", 0.52, "ConcurrentHashMap 原理", 0.74);
        when(embeddingService.embedBatch(anyList())).thenAnswer(invocation -> {
            List<String> titles = invocation.getArgument(0);
            return titles.stream().map(t -> unit(cosByTitle.get(t))).toList();
        });

        CourseRecommender.Result result = recommender.recommend(List.of(hit(2007L, 0.66)), QUESTION, STUDENT);

        assertThat(result.cards().get(0).lessons()).extracting(RecommendationCard.LessonCard::lessonId)
                .containsExactly("4024", "4022");
        assertThat(result.stored().get(0).lessonIds()).containsExactly("4024", "4022");
    }

    @Test
    void noLessonAboveThresholdStillRecommendsCourse() {
        when(outlineService.outlines(any(), any(), any())).thenReturn(R.ok(List.of(outline(2005L,
                lesson(4011L, "数据类型与变量")))));
        when(embeddingService.embedBatch(anyList())).thenReturn(List.of(unit(0.20)));

        CourseRecommender.Result result = recommender.recommend(List.of(hit(2005L, 0.60)), QUESTION, STUDENT);

        assertThat(result.cards()).hasSize(1);
        assertThat(result.cards().get(0).lessons()).isEmpty();
    }

    @Test
    void lessonTitleVectorsAreCached() {
        when(outlineService.outlines(any(), any(), any())).thenReturn(R.ok(List.of(outline(2007L,
                lesson(4024L, "ConcurrentHashMap 原理")))));
        when(embeddingService.embedBatch(anyList())).thenReturn(List.of(unit(0.74)));

        recommender.recommend(List.of(hit(2007L, 0.66)), QUESTION, STUDENT);
        recommender.recommend(List.of(hit(2007L, 0.66)), QUESTION, STUDENT);

        verify(embeddingService, times(1)).embedBatch(anyList());
    }

    @Test
    void courseServiceFailureMeansNoRecommendationNotAnError() {
        when(outlineService.outlines(any(), any(), any())).thenThrow(new IllegalStateException("课程服务不可用"));

        assertThat(recommender.recommend(List.of(hit(2007L, 0.80)), QUESTION, STUDENT).cards()).isEmpty();
    }

    @Test
    void nothingAboveThresholdNeverCallsCourseService() {
        assertThat(recommender.recommend(List.of(hit(2007L, 0.32)), QUESTION, STUDENT).cards()).isEmpty();
        verify(outlineService, never()).outlines(any(), any(), any());
    }

    @Test
    void resolveRefreshesCardsInOneCallAndKeepsStoredLessonOrder() {
        CourseOutline bought = outline(2007L, lesson(4022L, "线程安全问题的根源"), lesson(4024L, "ConcurrentHashMap 原理"));
        bought.setCanStudyAll(true); // 学生后来买了这门课
        when(outlineService.outlines(any(), any(), any())).thenReturn(R.ok(List.of(bought)));

        List<List<RecommendationCard>> cards = recommender.resolve(List.of(
                List.of(),
                List.of(new StoredRecommendation("2007", 0.66, List.of("4024", "4022")),
                        new StoredRecommendation("2002", 0.70, List.of()))), STUDENT);

        verify(outlineService, times(1)).outlines(any(), any(), any());
        assertThat(cards).hasSize(2);
        assertThat(cards.get(0)).isEmpty();
        assertThat(cards.get(1)).extracting(RecommendationCard::courseId).containsExactly("2007"); // 2002 已下架
        assertThat(cards.get(1).get(0).purchased()).isTrue();
        assertThat(cards.get(1).get(0).lessons()).extracting(RecommendationCard.LessonCard::lessonId)
                .containsExactly("4024", "4022");
    }

    private static RetrievedChunk hit(Long courseId, double cosine) {
        return new RetrievedChunk(92000L, courseId, cosine, "资料", "内容");
    }

    private static CourseOutline outline(long courseId, LessonOutline... lessons) {
        CourseOutline outline = new CourseOutline();
        outline.setCourseId(courseId);
        outline.setTitle("课程 " + courseId);
        outline.setCoverUrl("https://dummyimage.com/600x340.png");
        outline.setPrice(new BigDecimal("99.00"));
        outline.setPreviewable(true);
        outline.setLessons(List.of(lessons));
        return outline;
    }

    private static LessonOutline lesson(long lessonId, String title) {
        LessonOutline lesson = new LessonOutline();
        lesson.setLessonId(lessonId);
        lesson.setChapterId(3000L + lessonId % 10);
        lesson.setTitle(title);
        lesson.setDuration(900);
        lesson.setChapterIndex(1);
        lesson.setLessonIndex(1);
        return lesson;
    }

    private static List<Float> unit(double cos) {
        return List.of((float) cos, (float) Math.sqrt(1 - cos * cos));
    }
}
