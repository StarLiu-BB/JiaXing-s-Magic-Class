package com.zhixue.course.service;

import com.zhixue.course.domain.entity.Category;

import java.util.List;

public interface CategoryService {

    List<Category> list();

    Category getById(Long id);

    boolean save(Category category);

    boolean update(Category category);

    boolean remove(Long id);
}
