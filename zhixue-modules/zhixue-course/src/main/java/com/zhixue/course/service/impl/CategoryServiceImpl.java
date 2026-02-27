package com.zhixue.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixue.course.domain.entity.Category;
import com.zhixue.course.mapper.CategoryMapper;
import com.zhixue.course.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 课程分类服务实现类。
 * 负责处理课程分类相关的业务逻辑，包括查询、新增、修改、删除分类等操作。
 */
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;

    /**
     * 查询所有启用的课程分类，按排序号升序排列。
     * @return 分类列表
     */
    @Override
    public List<Category> list() {
        LambdaQueryWrapper<Category> qw = new LambdaQueryWrapper<>();
        qw.eq(Category::getStatus, 1)
          .orderByAsc(Category::getSort);
        return categoryMapper.selectList(qw);
    }

    /**
     * 根据分类编号查询分类详情。
     * @param id 分类编号
     * @return 分类信息
     */
    @Override
    public Category getById(Long id) {
        return categoryMapper.selectById(id);
    }

    /**
     * 新增一个课程分类。
     * @param category 分类信息
     * @return 是否成功
     */
    @Override
    public boolean save(Category category) {
        return categoryMapper.insert(category) > 0;
    }

    /**
     * 修改课程分类信息。
     * @param category 分类信息
     * @return 是否成功
     */
    @Override
    public boolean update(Category category) {
        return categoryMapper.updateById(category) > 0;
    }

    /**
     * 删除指定的课程分类。
     * @param id 分类编号
     * @return 是否成功
     */
    @Override
    public boolean remove(Long id) {
        return categoryMapper.deleteById(id) > 0;
    }
}
