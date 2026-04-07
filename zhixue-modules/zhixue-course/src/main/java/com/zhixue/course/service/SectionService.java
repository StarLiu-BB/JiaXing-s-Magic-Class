package com.zhixue.course.service;

import com.zhixue.course.domain.entity.Section;

import java.util.List;

/**
 * 小节服务。
 */
public interface SectionService {

    List<Section> listByChapter(Long chapterId);

    Section getById(Long id);

    boolean saveSection(Section section);

    boolean updateSection(Section section);

    boolean removeSection(Long id);
}
