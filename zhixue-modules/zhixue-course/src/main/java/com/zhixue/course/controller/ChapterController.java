package com.zhixue.course.controller;

import com.zhixue.common.core.domain.R;
import com.zhixue.common.security.annotation.RequirePermission;
import com.zhixue.course.domain.entity.Chapter;
import com.zhixue.course.service.ChapterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 章节管理接口。
 * 这个类负责处理前端发来的关于课程章节的所有请求，包括查询、新增、修改、删除章节等操作。
 * 章节是课程内容的组织单元，一个课程包含多个章节，每个章节包含多个小节。
 */
@RestController
@RequestMapping
@RequiredArgsConstructor
public class ChapterController {

    private final ChapterService chapterService;

    /**
     * 查询指定课程下的所有章节列表。
     * @param courseId 课程编号
     * @return 章节列表数据
     */
    @GetMapping({"/chapter/list/{courseId}", "/{courseId}/chapters"})
    public R<List<Chapter>> list(@PathVariable Long courseId) {
        return R.ok(chapterService.listByCourse(courseId));
    }

    /**
     * 根据章节编号查询章节详情。
     * @param id 章节编号
     * @return 章节详情数据
     */
    @GetMapping({"/chapter/info/{id}", "/chapter/{id}"})
    public R<Chapter> info(@PathVariable Long id) {
        return R.ok(chapterService.getById(id));
    }

    /**
     * 新增一个章节。
     * @param chapter 章节信息
     * @return 操作结果
     */
    @PostMapping("/chapter")
    @RequirePermission("course:edit")
    public R<Void> create(@RequestBody Chapter chapter) {
        return chapterService.saveChapter(chapter) ? R.ok() : R.fail("新增失败");
    }

    /**
     * 修改章节信息。
     * @param chapter 章节信息
     * @return 操作结果
     */
    @PutMapping("/chapter")
    @RequirePermission("course:edit")
    public R<Void> update(@RequestBody Chapter chapter) {
        return chapterService.updateChapter(chapter) ? R.ok() : R.fail("更新失败");
    }

    /**
     * 删除指定的章节。
     * @param id 章节编号
     * @return 操作结果
     */
    @DeleteMapping("/chapter/{id}")
    @RequirePermission("course:edit")
    public R<Void> delete(@PathVariable Long id) {
        return chapterService.removeChapter(id) ? R.ok() : R.fail("删除失败");
    }
}
