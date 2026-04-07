package com.zhixue.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixue.course.domain.entity.Chapter;
import com.zhixue.course.domain.entity.Section;
import com.zhixue.course.mapper.ChapterMapper;
import com.zhixue.course.mapper.SectionMapper;
import com.zhixue.course.service.ChapterService;
import com.zhixue.course.service.support.CourseAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 章节服务实现类。
 * 负责处理课程章节相关的业务逻辑，包括查询、新增、修改、删除章节等操作。
 * 章节是课程内容的组织单元，一个课程包含多个章节，每个章节包含多个小节。
 */
@Service
@RequiredArgsConstructor
public class ChapterServiceImpl implements ChapterService {

    private final ChapterMapper chapterMapper;
    private final SectionMapper sectionMapper;
    private final CourseAccessGuard courseAccessGuard;

    /**
     * 查询指定课程下的所有章节，按序号升序排列。
     * @param courseId 课程编号
     * @return 章节列表
     */
    @Override
    public List<Chapter> listByCourse(Long courseId) {
        courseAccessGuard.requireReadableCourse(courseId);
        LambdaQueryWrapper<Chapter> qw = new LambdaQueryWrapper<>();
        qw.eq(Chapter::getCourseId, courseId)
          .orderByAsc(Chapter::getOrderNum);
        List<Chapter> chapters = chapterMapper.selectList(qw);
        if (chapters.isEmpty()) {
            return chapters;
        }
        List<Section> sections = sectionMapper.selectList(new LambdaQueryWrapper<Section>()
                .eq(Section::getCourseId, courseId)
                .orderByAsc(Section::getOrderNum));
        Map<Long, List<Section>> sectionMap = sections.stream()
                .collect(Collectors.groupingBy(Section::getChapterId));
        chapters.forEach(chapter -> chapter.setLessons(sectionMap.getOrDefault(chapter.getId(), Collections.emptyList())));
        return chapters;
    }

    /**
     * 根据章节编号查询章节详情。
     * @param id 章节编号
     * @return 章节信息
     */
    @Override
    public Chapter getById(Long id) {
        return courseAccessGuard.requireReadableChapter(id);
    }

    /**
     * 新增一个章节。
     * @param chapter 章节信息
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveChapter(Chapter chapter) {
        courseAccessGuard.requireWritableCourse(chapter.getCourseId());
        if (chapter.getOrderNum() == null) {
            chapter.setOrderNum(0);
        }
        return chapterMapper.insert(chapter) > 0;
    }

    /**
     * 修改章节信息。
     * @param chapter 章节信息
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateChapter(Chapter chapter) {
        Chapter existing = courseAccessGuard.requireWritableChapter(chapter.getId());
        if (chapter.getCourseId() == null) {
            chapter.setCourseId(existing.getCourseId());
        } else {
            courseAccessGuard.requireWritableCourse(chapter.getCourseId());
        }
        return chapterMapper.updateById(chapter) > 0;
    }

    /**
     * 删除指定的章节。
     * @param id 章节编号
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeChapter(Long id) {
        courseAccessGuard.requireWritableChapter(id);
        LambdaQueryWrapper<Section> sectionWrapper = new LambdaQueryWrapper<>();
        sectionWrapper.eq(Section::getChapterId, id);
        sectionMapper.delete(sectionWrapper);
        return chapterMapper.deleteById(id) > 0;
    }
}
