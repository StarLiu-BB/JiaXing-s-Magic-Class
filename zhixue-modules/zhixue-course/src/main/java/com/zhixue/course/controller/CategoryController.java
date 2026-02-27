package com.zhixue.course.controller;

import com.zhixue.common.core.domain.R;
import com.zhixue.course.domain.entity.Category;
import com.zhixue.course.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 课程分类管理接口。
 * 这个类负责处理前端发来的关于课程分类的所有请求，包括查询、新增、修改、删除分类等操作。
 */
@RestController
@RequestMapping("/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * 查询所有启用的课程分类列表。
     * @return 分类列表数据
     */
    @GetMapping("/list")
    public R<List<Category>> list() {
        return R.ok(categoryService.list());
    }

    /**
     * 根据分类编号查询分类详情。
     * @param id 分类编号
     * @return 分类详情数据
     */
    @GetMapping("/{id}")
    public R<Category> getById(@PathVariable Long id) {
        return R.ok(categoryService.getById(id));
    }

    /**
     * 新增一个课程分类。
     * @param category 分类信息
     * @return 操作结果
     */
    @PostMapping
    public R<Void> save(@RequestBody Category category) {
        return categoryService.save(category) ? R.ok() : R.fail("新增失败");
    }

    /**
     * 修改课程分类信息。
     * @param category 分类信息
     * @return 操作结果
     */
    @PutMapping
    public R<Void> update(@RequestBody Category category) {
        return categoryService.update(category) ? R.ok() : R.fail("更新失败");
    }

    /**
     * 删除指定的课程分类。
     * @param id 分类编号
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        return categoryService.remove(id) ? R.ok() : R.fail("删除失败");
    }
}
