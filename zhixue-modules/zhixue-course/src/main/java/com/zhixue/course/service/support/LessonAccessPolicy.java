package com.zhixue.course.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixue.api.order.RemoteOrderService;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.security.config.SecurityProperties;
import com.zhixue.common.security.utils.SecurityUtils;
import com.zhixue.course.domain.entity.Chapter;
import com.zhixue.course.domain.entity.Course;
import com.zhixue.course.domain.entity.Section;
import com.zhixue.course.mapper.ChapterMapper;
import com.zhixue.course.mapper.SectionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 课时观看权限：决定哪些课时可以下发视频地址。
 *
 * <p>可观看全部课时：免费课、管理员、本课教师、已购买用户（以订单服务为唯一事实来源，
 * 支付即生效、退款即失效）。其余用户只能观看试看课时 —— 按章节序、课时序取前
 * previewLessonCount 节；其它课时的 videoUrl 置空并标记 locked。</p>
 *
 * <p>所有向客户端返回课时的读接口都必须经过 {@link #mask}，这是付费内容的唯一闸口。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LessonAccessPolicy {

    private final ChapterMapper chapterMapper;
    private final SectionMapper sectionMapper;
    private final RemoteOrderService remoteOrderService;
    private final SecurityProperties securityProperties;

    /**
     * 当前用户能否观看该课程的全部课时。
     */
    public boolean canStudyAll(Course course) {
        if (course.getPrice() == null || course.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            return false;
        }
        // 只有本课教师豁免；持有课程管理权限的其他教师不能借此白看
        if (SecurityUtils.hasAnyRole("ADMIN") || Objects.equals(course.getTeacherId(), userId)) {
            return true;
        }
        return hasPurchased(userId, course.getId());
    }

    /**
     * 就地处理即将返回给客户端的课时：标注试看，并清除无权观看课时的视频地址。
     */
    public void mask(Course course, Collection<Section> lessons) {
        if (lessons == null || lessons.isEmpty()) {
            return;
        }
        boolean full = canStudyAll(course);
        Set<Long> previewIds = previewLessonIds(course);
        for (Section lesson : lessons) {
            boolean preview = previewIds.contains(lesson.getId());
            boolean playable = full || preview;
            lesson.setPreview(preview);
            lesson.setLocked(!playable);
            if (!playable) {
                lesson.setVideoUrl(null);
            }
        }
    }

    /**
     * 查询失败一律按未购买处理（fail-closed）：宁可让买家暂时看不了，也不能把付费内容放给所有人。
     */
    private boolean hasPurchased(Long userId, Long courseId) {
        try {
            R<Boolean> result = remoteOrderService.hasPurchased(
                    userId, courseId, "true", securityProperties.getInternalToken());
            if (result == null || !result.isSuccess()) {
                log.warn("查询购买关系失败，按未购买处理 userId={}, courseId={}, msg={}",
                        userId, courseId, result == null ? null : result.getMsg());
                return false;
            }
            return Boolean.TRUE.equals(result.getData());
        } catch (Exception e) {
            log.error("调用订单服务查询购买关系异常，按未购买处理 userId={}, courseId={}", userId, courseId, e);
            return false;
        }
    }

    private Set<Long> previewLessonIds(Course course) {
        int count = course.getPreviewLessonCount() == null ? 0 : course.getPreviewLessonCount();
        if (!Integer.valueOf(1).equals(course.getAllowPreview()) || count <= 0) {
            return Set.of();
        }
        List<Chapter> chapters = chapterMapper.selectList(new LambdaQueryWrapper<Chapter>()
                .eq(Chapter::getCourseId, course.getId()));
        Map<Long, Integer> chapterOrder = new HashMap<>();
        chapters.forEach(c -> chapterOrder.put(c.getId(), c.getOrderNum() == null ? Integer.MAX_VALUE : c.getOrderNum()));

        List<Section> sections = sectionMapper.selectList(new LambdaQueryWrapper<Section>()
                .eq(Section::getCourseId, course.getId()));
        return sections.stream()
                .sorted(Comparator
                        .comparing((Section s) -> chapterOrder.getOrDefault(s.getChapterId(), Integer.MAX_VALUE))
                        .thenComparing(s -> s.getOrderNum() == null ? Integer.MAX_VALUE : s.getOrderNum())
                        .thenComparing(Section::getId))
                .limit(count)
                .map(Section::getId)
                .collect(Collectors.toSet());
    }
}
