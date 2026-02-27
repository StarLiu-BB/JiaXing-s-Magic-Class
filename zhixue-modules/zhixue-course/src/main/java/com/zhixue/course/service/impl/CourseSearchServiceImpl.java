package com.zhixue.course.service.impl;

import com.zhixue.course.domain.doc.CourseDoc;
import com.zhixue.course.service.CourseSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitSupport;
import org.springframework.data.elasticsearch.core.SearchPage;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 课程搜索服务实现。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class CourseSearchServiceImpl implements CourseSearchService {

    private final ElasticsearchOperations elasticsearchOperations;

    @Override
    public void save(CourseDoc doc) {
        elasticsearchOperations.save(doc);
    }

    @Override
    public void delete(Long id) {
        elasticsearchOperations.delete(id.toString(), CourseDoc.class);
    }

    @Override
    public Page<CourseDoc> search(String keyword, int page, int size) {
        Criteria criteria = new Criteria("title").contains(keyword)
                .or(new Criteria("description").contains(keyword));
        CriteriaQuery query = new CriteriaQuery(criteria, PageRequest.of(page, size));
        SearchHits<CourseDoc> hits = elasticsearchOperations.search(query, CourseDoc.class);
        SearchPage<CourseDoc> searchPage = SearchHitSupport.searchPageFor(hits, query.getPageable());
        return (Page<CourseDoc>) SearchHitSupport.unwrapSearchHits(searchPage);
    }
}

