package com.zhixue.course.service.impl;

import com.zhixue.course.domain.doc.CourseDoc;
import com.zhixue.course.service.CourseSearchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 默认的课程搜索降级实现。
 * 第一阶段未接入 Elasticsearch 时返回空结果，避免核心链路启动受阻。
 */
@Service
@ConditionalOnProperty(name = "zhixue.integration.search.mode", havingValue = "stub", matchIfMissing = true)
public class CourseSearchStubServiceImpl implements CourseSearchService {

    @Override
    public void save(CourseDoc doc) {
        // Phase 1 keeps search in stub mode by default.
    }

    @Override
    public void delete(Long id) {
        // Phase 1 keeps search in stub mode by default.
    }

    @Override
    public Page<CourseDoc> search(String keyword, int page, int size) {
        return new PageImpl<>(Collections.emptyList(), PageRequest.of(page, size), 0);
    }
}
