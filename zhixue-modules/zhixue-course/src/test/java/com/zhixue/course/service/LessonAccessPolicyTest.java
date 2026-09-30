package com.zhixue.course.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixue.api.order.RemoteOrderService;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.security.config.InternalAccessConstants;
import com.zhixue.common.security.config.SecurityProperties;
import com.zhixue.course.domain.entity.Chapter;
import com.zhixue.course.domain.entity.Course;
import com.zhixue.course.domain.entity.Section;
import com.zhixue.course.mapper.ChapterMapper;
import com.zhixue.course.mapper.SectionMapper;
import com.zhixue.course.service.support.LessonAccessPolicy;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 付费课时访问控制测试。
 *
 * <p>修复前实测：匿名请求 /course/2001/chapters 即可拿到 199 元课程全部 3 个课时的视频地址；
 * 后端没有任何"是否已购买"的判断，付费课与免费课没有区别。</p>
 *
 * <p>规则：免费课、本课教师、管理员、已购买用户可看全部；其余用户只能看试看课时
 * （按章节序→课时序取前 previewLessonCount 节），其它课时不下发视频地址。</p>
 */
@ExtendWith(MockitoExtension.class)
class LessonAccessPolicyTest {

    private static final String TOKEN = "unit-test-internal-token-0123456789";
    private static final long BUYER = 1003L;
    private static final long TEACHER = 1002L;

    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Chapter.class);
        TableInfoHelper.initTableInfo(assistant, Section.class);
    }

    @Mock
    private ChapterMapper chapterMapper;
    @Mock
    private SectionMapper sectionMapper;
    @Mock
    private RemoteOrderService remoteOrderService;

    private LessonAccessPolicy policy;

    @BeforeEach
    void setUp() {
        SecurityProperties properties = new SecurityProperties();
        properties.setInternalToken(TOKEN);
        policy = new LessonAccessPolicy(chapterMapper, sectionMapper, remoteOrderService, properties);

        // 课程 2001：章节 3001(序1) 含 4001；章节 3002(序2) 含 4002、4003；试看前 2 节
        lenient().when(chapterMapper.selectList(any())).thenReturn(List.of(chapter(3002L, 2), chapter(3001L, 1)));
        lenient().when(sectionMapper.selectList(any())).thenReturn(List.of(
                section(4003L, 3002L, 2), section(4002L, 3002L, 1), section(4001L, 3001L, 1)));
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void loginAs(long userId, String roles) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(InternalAccessConstants.TRUSTED_REQUEST_ATTR, Boolean.TRUE);
        request.addHeader(InternalAccessConstants.USER_ID_HEADER, String.valueOf(userId));
        request.addHeader(InternalAccessConstants.USER_ROLES_HEADER, roles);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private Course paidCourse() {
        Course course = new Course();
        course.setId(2001L);
        course.setTeacherId(TEACHER);
        course.setPrice(new BigDecimal("199.00"));
        course.setAllowPreview(1);
        course.setPreviewLessonCount(2);
        return course;
    }

    private Chapter chapter(long id, int order) {
        Chapter chapter = new Chapter();
        chapter.setId(id);
        chapter.setCourseId(2001L);
        chapter.setOrderNum(order);
        return chapter;
    }

    private Section section(long id, long chapterId, int order) {
        Section section = new Section();
        section.setId(id);
        section.setCourseId(2001L);
        section.setChapterId(chapterId);
        section.setOrderNum(order);
        section.setVideoUrl("https://example.com/videos/" + id + ".mp4");
        return section;
    }

    /** 模拟接口返回给客户端的课时（独立对象，由策略就地脱敏）。 */
    private List<Section> lessonsForClient() {
        return List.of(section(4001L, 3001L, 1), section(4002L, 3002L, 1), section(4003L, 3002L, 2));
    }

    private Section byId(List<Section> lessons, long id) {
        return lessons.stream().filter(s -> s.getId() == id).findFirst().orElseThrow();
    }

    @Test
    void anonymousShouldOnlyGetPreviewLessonUrls() {
        List<Section> lessons = lessonsForClient();

        policy.mask(paidCourse(), lessons);

        assertThat(byId(lessons, 4001L).getVideoUrl()).isNotNull();
        assertThat(byId(lessons, 4001L).getPreview()).isTrue();
        assertThat(byId(lessons, 4002L).getVideoUrl()).isNotNull();
        assertThat(byId(lessons, 4002L).getPreview()).isTrue();
        assertThat(byId(lessons, 4003L).getVideoUrl()).as("非试看课时不得下发视频地址").isNull();
        assertThat(byId(lessons, 4003L).getLocked()).isTrue();
        // 匿名用户无需查询订单服务
        verify(remoteOrderService, never()).hasPurchased(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void buyerShouldGetAllLessonUrlsViaTrustedInternalCall() {
        loginAs(BUYER, "STUDENT");
        when(remoteOrderService.hasPurchased(BUYER, 2001L, "true", TOKEN)).thenReturn(R.ok(true));
        List<Section> lessons = lessonsForClient();

        policy.mask(paidCourse(), lessons);

        assertThat(lessons).allSatisfy(s -> {
            assertThat(s.getVideoUrl()).isNotNull();
            assertThat(s.getLocked()).isFalse();
        });
    }

    @Test
    void loggedInNonBuyerShouldStillBeLimitedToPreview() {
        loginAs(BUYER, "STUDENT");
        when(remoteOrderService.hasPurchased(BUYER, 2001L, "true", TOKEN)).thenReturn(R.ok(false));
        List<Section> lessons = lessonsForClient();

        policy.mask(paidCourse(), lessons);

        assertThat(byId(lessons, 4003L).getVideoUrl()).isNull();
    }

    @Test
    void shouldFailClosedWhenOrderServiceUnavailable() {
        // 查不到购买关系时按未购买处理：宁可让买家暂时看不了，也不能把付费内容放给所有人
        loginAs(BUYER, "STUDENT");
        when(remoteOrderService.hasPurchased(BUYER, 2001L, "true", TOKEN)).thenThrow(new RuntimeException("timeout"));
        List<Section> lessons = lessonsForClient();

        policy.mask(paidCourse(), lessons);

        assertThat(byId(lessons, 4003L).getVideoUrl()).isNull();
    }

    @Test
    void shouldFailClosedWhenOrderServiceReturnsFailure() {
        loginAs(BUYER, "STUDENT");
        when(remoteOrderService.hasPurchased(BUYER, 2001L, "true", TOKEN)).thenReturn(R.fail(403, "禁止外部访问"));
        List<Section> lessons = lessonsForClient();

        policy.mask(paidCourse(), lessons);

        assertThat(byId(lessons, 4003L).getVideoUrl()).isNull();
    }

    @Test
    void freeCourseShouldBeFullyWatchable() {
        Course course = paidCourse();
        course.setPrice(BigDecimal.ZERO);
        List<Section> lessons = lessonsForClient();

        policy.mask(course, lessons);

        assertThat(lessons).allSatisfy(s -> assertThat(s.getVideoUrl()).isNotNull());
        assertThat(policy.canStudyAll(course)).isTrue();
    }

    @Test
    void courseTeacherAndAdminShouldSeeEverythingWithoutPurchase() {
        loginAs(TEACHER, "TEACHER");
        List<Section> teacherView = lessonsForClient();
        policy.mask(paidCourse(), teacherView);
        assertThat(teacherView).allSatisfy(s -> assertThat(s.getVideoUrl()).isNotNull());

        loginAs(1001L, "ADMIN");
        List<Section> adminView = lessonsForClient();
        policy.mask(paidCourse(), adminView);
        assertThat(adminView).allSatisfy(s -> assertThat(s.getVideoUrl()).isNotNull());

        verify(remoteOrderService, never()).hasPurchased(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void otherTeacherMustNotBypassPurchase() {
        // 只有本课教师豁免；持有课程管理权限的其他教师不能借此白看
        loginAs(1099L, "TEACHER");
        when(remoteOrderService.hasPurchased(1099L, 2001L, "true", TOKEN)).thenReturn(R.ok(false));
        List<Section> lessons = lessonsForClient();

        policy.mask(paidCourse(), lessons);

        assertThat(byId(lessons, 4003L).getVideoUrl()).isNull();
    }

    @Test
    void previewDisabledShouldLockAllLessonsForNonBuyer() {
        Course course = paidCourse();
        course.setAllowPreview(0);
        List<Section> lessons = lessonsForClient();

        policy.mask(course, lessons);

        assertThat(lessons).allSatisfy(s -> {
            assertThat(s.getVideoUrl()).isNull();
            assertThat(s.getPreview()).isFalse();
        });
    }

    @Test
    void canStudyAllShouldReflectPurchase() {
        assertThat(policy.canStudyAll(paidCourse())).as("匿名").isFalse();

        loginAs(BUYER, "STUDENT");
        when(remoteOrderService.hasPurchased(BUYER, 2001L, "true", TOKEN)).thenReturn(R.ok(true));
        assertThat(policy.canStudyAll(paidCourse())).as("已购").isTrue();
    }
}
