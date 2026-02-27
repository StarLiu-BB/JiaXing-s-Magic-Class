package com.zhixue.course.controller;

import com.zhixue.common.core.domain.R;
import com.zhixue.course.domain.entity.Section;
import com.zhixue.course.service.impl.SectionServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 小节管理接口。
 * 这个类负责处理前端发来的关于课程小节的所有请求，包括查询、新增、修改、删除小节等操作。
 * 小节是课程内容的最小单元，通常包含一个视频或文档。
 */
@RestController
@RequestMapping("/course/section")
@RequiredArgsConstructor
public class SectionController {

    private final SectionServiceImpl sectionService;

    /**
     * 查询指定章节下的所有小节列表。
     * @param chapterId 章节编号
     * @return 小节列表数据
     */
    @GetMapping("/list/{chapterId}")
    public R<List<Section>> list(@PathVariable Long chapterId) {
        return R.ok(sectionService.listByChapter(chapterId));
    }

    /**
     * 根据小节编号查询小节详情。
     * @param id 小节编号
     * @return 小节详情数据
     */
    @GetMapping("/info/{id}")
    public R<Section> info(@PathVariable Long id) {
        return R.ok(sectionService.getById(id));
    }

    /**
     * 新增一个小节。
     * @param section 小节信息
     * @return 操作结果
     */
    @PostMapping
    public R<Void> create(@RequestBody Section section) {
        return sectionService.saveSection(section) ? R.ok() : R.fail("新增失败");
    }

    /**
     * 修改小节信息。
     * @param section 小节信息
     * @return 操作结果
     */
    @PutMapping
    public R<Void> update(@RequestBody Section section) {
        return sectionService.updateSection(section) ? R.ok() : R.fail("更新失败");
    }

    /**
     * 删除指定的小节。
     * @param id 小节编号
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        return sectionService.removeSection(id) ? R.ok() : R.fail("删除失败");
    }
}

