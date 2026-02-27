package com.zhixue.course.service;

import com.zhixue.course.domain.entity.Chapter;

import java.util.List;

/**
 * <p>
 * 章节服务。
 * </p>
 */
public interface ChapterService {

    List<Chapter> listByCourse(Long courseId);

    Chapter getById(Long id);

    boolean saveChapter(Chapter chapter);

    boolean updateChapter(Chapter chapter);

    boolean removeChapter(Long id);
}

