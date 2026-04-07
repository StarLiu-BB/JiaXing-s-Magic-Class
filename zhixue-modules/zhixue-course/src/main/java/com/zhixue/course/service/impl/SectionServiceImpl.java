package com.zhixue.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.course.domain.entity.Chapter;
import com.zhixue.course.domain.entity.Section;
import com.zhixue.course.mapper.SectionMapper;
import com.zhixue.course.service.SectionService;
import com.zhixue.course.service.support.CourseAccessGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 小节服务实现类。
 * 负责处理课程小节相关的业务逻辑，包括查询、新增、修改、删除小节等操作。
 * 小节是课程内容的最小单元，通常包含一个视频或文档。
 */
@Service
@RequiredArgsConstructor
public class SectionServiceImpl implements SectionService {

    private final SectionMapper sectionMapper;
    private final CourseAccessGuard courseAccessGuard;

    /**
     * 查询指定章节下的所有小节，按序号升序排列。
     * @param chapterId 章节编号
     * @return 小节列表
     */
    @Override
    public List<Section> listByChapter(Long chapterId) {
        courseAccessGuard.requireReadableChapter(chapterId);
        LambdaQueryWrapper<Section> qw = new LambdaQueryWrapper<>();
        qw.eq(Section::getChapterId, chapterId)
          .orderByAsc(Section::getOrderNum);
        return sectionMapper.selectList(qw);
    }

    /**
     * 根据小节编号查询小节详情。
     * @param id 小节编号
     * @return 小节信息
     */
    @Override
    public Section getById(Long id) {
        return courseAccessGuard.requireReadableSection(id);
    }

    /**
     * 新增一个小节。
     * @param section 小节信息
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean saveSection(Section section) {
        Chapter chapter = courseAccessGuard.requireWritableChapter(section.getChapterId());
        if (section.getCourseId() == null) {
            section.setCourseId(chapter.getCourseId());
        } else if (!section.getCourseId().equals(chapter.getCourseId())) {
            throw new ServiceException("课时所属课程与章节不一致");
        }
        if (section.getStatus() == null) {
            section.setStatus(0);
        }
        if (section.getOrderNum() == null) {
            section.setOrderNum(0);
        }
        return sectionMapper.insert(section) > 0;
    }

    /**
     * 修改小节信息。
     * @param section 小节信息
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean updateSection(Section section) {
        Section existing = courseAccessGuard.requireWritableSection(section.getId());
        if (section.getChapterId() == null) {
            section.setChapterId(existing.getChapterId());
        } else {
            Chapter chapter = courseAccessGuard.requireWritableChapter(section.getChapterId());
            if (section.getCourseId() != null && !section.getCourseId().equals(chapter.getCourseId())) {
                throw new ServiceException("课时所属课程与章节不一致");
            }
            section.setCourseId(chapter.getCourseId());
        }
        if (section.getCourseId() == null) {
            section.setCourseId(existing.getCourseId());
        }
        return sectionMapper.updateById(section) > 0;
    }

    /**
     * 删除指定的小节。
     * @param id 小节编号
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean removeSection(Long id) {
        courseAccessGuard.requireWritableSection(id);
        return sectionMapper.deleteById(id) > 0;
    }
}
