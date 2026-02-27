package com.zhixue.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixue.course.domain.entity.Chapter;
import com.zhixue.course.mapper.ChapterMapper;
import com.zhixue.course.service.ChapterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 章节服务实现类。
 * 负责处理课程章节相关的业务逻辑，包括查询、新增、修改、删除章节等操作。
 * 章节是课程内容的组织单元，一个课程包含多个章节，每个章节包含多个小节。
 */
@Service
@RequiredArgsConstructor
public class ChapterServiceImpl implements ChapterService {

    private final ChapterMapper chapterMapper;

    /**
     * 查询指定课程下的所有章节，按序号升序排列。
     * @param courseId 课程编号
     * @return 章节列表
     */
    @Override
    public List<Chapter> listByCourse(Long courseId) {
        LambdaQueryWrapper<Chapter> qw = new LambdaQueryWrapper<>();
        qw.eq(Chapter::getCourseId, courseId)
          .orderByAsc(Chapter::getOrderNum);
        return chapterMapper.selectList(qw);
    }

    /**
     * 根据章节编号查询章节详情。
     * @param id 章节编号
     * @return 章节信息
     */
    @Override
    public Chapter getById(Long id) {
        return chapterMapper.selectById(id);
    }

    /**
     * 新增一个章节。
     * @param chapter 章节信息
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveChapter(Chapter chapter) {
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
        return chapterMapper.deleteById(id) > 0;
    }
}

