package com.zhixue.common.core.domain;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页查询结果类
 * 作用：这个类用来包装分页查询的结果返回给前端。
 * 当前端请求分页数据时，除了返回数据列表，还要告诉它总共有多少条、总共有多少页。
 * 这样前端就能知道还有没有下一页，以及显示页码导航。
 *
 * @param <T> 实际返回的数据类型
 */
@Data
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 当前页的数据列表，默认是空列表
    private List<T> records = Collections.emptyList();
    // 总共有多少条数据
    private long total;
    // 总共有多少页
    private long pages;

    // 创建一个分页结果对象
    public static <T> PageResult<T> of(List<T> records, long total, long pageSize) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(total);
        // 计算总页数，比如总共100条，每页10条，就是10页
        long size = Math.max(pageSize, 1);
        result.setPages((total + size - 1) / size);
        return result;
    }
}

