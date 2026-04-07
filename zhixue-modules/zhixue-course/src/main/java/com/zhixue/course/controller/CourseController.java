package com.zhixue.course.controller;

import com.zhixue.common.core.domain.PageQuery;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.security.annotation.RequirePermission;
import com.zhixue.common.security.utils.SecurityUtils;
import com.zhixue.course.domain.entity.Chapter;
import com.zhixue.course.domain.entity.Course;
import com.zhixue.course.domain.doc.CourseDoc;
import com.zhixue.course.service.ChapterService;
import com.zhixue.course.service.CourseSearchService;
import com.zhixue.course.service.CourseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 课程管理接口。
 * 这个类负责处理前端发来的关于课程的所有请求，包括课程列表查询、课程详情、新增、修改、发布、上下架、删除以及课程搜索等操作。
 */
@RestController
@RequestMapping
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;
    private final CourseSearchService courseSearchService;
    private final ChapterService chapterService;

    /**
     * 分页查询课程列表。
     * @param query 分页查询参数，包括页码和每页数量
     * @param status 课程状态，可选参数，用于筛选草稿或已发布的课程
     * @param shelfStatus 上架状态，可选参数，用于筛选已上架或已下架的课程
     * @return 课程分页数据
     */
    @GetMapping("/list")
    public R<PageResult<Course>> list(PageQuery query,
                                      @RequestParam(required = false) String title,
                                      @RequestParam(required = false) Long categoryId,
                                      @RequestParam(required = false) Integer status,
                                      @RequestParam(required = false) Integer shelfStatus) {
        boolean canManageCourses = SecurityUtils.hasAnyPermission("course:list", "course:edit", "course:publish");
        if (!canManageCourses) {
            status = 1;
            shelfStatus = 1;
        }
        return R.ok(courseService.pageCourses(query, title, categoryId, status, shelfStatus));
    }

    @GetMapping("/banner/list")
    public R<List<Map<String, Object>>> banners(@RequestParam(defaultValue = "3") int limit) {
        return R.ok(courseService.listBanners(limit));
    }

    @GetMapping("/hot/list")
    public R<List<Course>> hot(@RequestParam(defaultValue = "6") int limit) {
        return R.ok(courseService.listHotCourses(limit));
    }

    @GetMapping("/latest/list")
    public R<List<Course>> latest(@RequestParam(defaultValue = "10") int limit) {
        return R.ok(courseService.listLatestCourses(limit));
    }

    /**
     * 根据课程编号查询课程详情。
     * @param id 课程编号
     * @return 课程详情数据
     */
    @GetMapping("/info/{id}")
    public R<Course> info(@PathVariable Long id) {
        Course course = courseService.getById(id);
        if (course == null) {
            return R.fail("课程不存在");
        }
        return R.ok(course);
    }

    @GetMapping("/{id}")
    public R<Course> detail(@PathVariable Long id) {
        return info(id);
    }

    @GetMapping("/detail/{id}")
    public R<Course> publicDetail(@PathVariable Long id) {
        return info(id);
    }

    @GetMapping("/detail/{id}/aggregate")
    public R<Map<String, Object>> aggregateDetail(@PathVariable Long id) {
        Course course = courseService.getById(id);
        List<Chapter> chapters = chapterService.listByCourse(id);
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("course", course);
        result.put("chapters", chapters);
        result.put("stats", Map.of("studyCount", 0, "favoriteCount", 0, "commentCount", 0));
        result.put("interactionSummary", Map.of("danmakuEnabled", true, "commentEnabled", true));
        return R.ok(result);
    }

    @GetMapping("/{id}/reviews")
    public R<List<Map<String, Object>>> reviews(@PathVariable Long id) {
        return R.ok(Collections.emptyList());
    }

    /**
     * 新增一门课程。
     * @param course 课程信息
     * @return 操作结果
     */
    @PostMapping
    @RequirePermission("course:edit")
    public R<Void> create(@Valid @RequestBody Course course) {
        return courseService.saveCourse(course) ? R.ok() : R.fail("新增失败");
    }

    /**
     * 修改课程信息。
     * @param course 课程信息
     * @return 操作结果
     */
    @PutMapping
    @RequirePermission("course:edit")
    public R<Void> update(@Valid @RequestBody Course course) {
        return courseService.updateCourse(course) ? R.ok() : R.fail("更新失败");
    }

    /**
     * 发布课程。
     * 将草稿状态的课程发布为正式课程。
     * @param id 课程编号
     * @return 操作结果
     */
    @PostMapping("/publish/{id}")
    @RequirePermission("course:publish")
    public R<Void> publish(@PathVariable Long id) {
        return courseService.publish(id) ? R.ok() : R.fail("发布失败");
    }

    @PutMapping("/{id}/publish")
    @RequirePermission("course:publish")
    public R<Void> publishWithPut(@PathVariable Long id) {
        return publish(id);
    }

    /**
     * 上架或下架课程。
     * @param id 课程编号
     * @param shelfStatus 上架状态，1表示上架，0表示下架
     * @return 操作结果
     */
    @PostMapping("/shelf/{id}")
    @RequirePermission({"course:publish", "course:offline"})
    public R<Void> shelf(@PathVariable Long id, @RequestParam Integer shelfStatus) {
        return courseService.shelf(id, shelfStatus) ? R.ok() : R.fail("上下架失败");
    }

    @PutMapping("/{id}/shelf")
    @RequirePermission({"course:publish", "course:offline"})
    public R<Void> shelfWithPut(@PathVariable Long id, @RequestParam Integer shelfStatus) {
        return shelf(id, shelfStatus);
    }

    @PutMapping("/{id}/offline")
    @RequirePermission({"course:publish", "course:offline"})
    public R<Void> offline(@PathVariable Long id) {
        return shelf(id, 0);
    }

    /**
     * 删除指定的课程。
     * @param id 课程编号
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("course:delete")
    public R<Void> delete(@PathVariable Long id) {
        return courseService.remove(id) ? R.ok() : R.fail("删除失败");
    }

    /**
     * 搜索课程。
     * 根据关键词在课程标题和描述中搜索匹配的课程。
     * @param keyword 搜索关键词
     * @param page 页码，从0开始
     * @param size 每页显示的数量
     * @return 搜索结果
     */
    @PostMapping("/search")
    public R<Page<CourseDoc>> search(@RequestParam String keyword,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "10") int size) {
        return R.ok(courseSearchService.search(keyword, page, size));
    }

}
