package com.zhixue.course.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.zhixue.course.domain.entity.Course;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 课程导入监听器。
 * 这个类用于 EasyExcel 框架解析 Excel 文件时，逐行读取课程数据并收集起来。
 * Excel 解析完成后，可以通过 getData 方法获取所有课程数据。
 */
@Slf4j
public class CourseImportListener extends AnalysisEventListener<Course> {

    /**
     * 存储解析出来的课程数据列表。
     */
    @Getter
    private final List<Course> data = new ArrayList<>();

    /**
     * 每解析一行 Excel 数据时调用。
     * 将解析出来的课程对象添加到数据列表中。
     * @param course 解析出来的课程对象
     * @param context 解析上下文
     */
    @Override
    public void invoke(Course course, AnalysisContext context) {
        data.add(course);
    }

    /**
     * 所有数据解析完成后调用。
     * 记录导入完成日志，显示总共导入的课程数量。
     * @param context 解析上下文
     */
    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        log.info("课程数据导入完成，总计 {} 条", data.size());
    }
}

