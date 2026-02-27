package com.zhixue.course.service;

import com.zhixue.course.domain.doc.CourseDoc;
import org.springframework.data.domain.Page;

/**
 * <p>
 * 课程搜索服务。
 * </p>
 */
public interface CourseSearchService {

    /**
     * 保存或更新课程索引。
     */
    void save(CourseDoc doc);

    /**
     * 删除索引。
     */
    void delete(Long id);

    /**
     * 关键字搜索。
     */
    Page<CourseDoc> search(String keyword, int page, int size);
}

