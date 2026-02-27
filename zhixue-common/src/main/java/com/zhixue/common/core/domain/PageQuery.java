package com.zhixue.common.core.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 分页查询参数类
 * 作用：这个类用来接收前端的分页查询请求。
 * 当数据很多时，不能一次性全部显示，需要分页展示。
 * 这个类告诉后端要查第几页、每页显示多少条、按什么字段排序。
 */
@Data
public class PageQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 当前页码，默认第1页，最少是1
    @Min(1)
    private int pageNum = 1;

    // 每页显示多少条，默认10条，最少1条最多200条
    @Min(1)
    @Max(200)
    private int pageSize = 10;

    // 按哪个字段排序
    private String sortBy;

    // 是否升序排列，true表示从小到大
    private boolean asc = true;

    // 计算从第几条开始查，比如第2页每页10条，就从第11条开始
    public long getOffset() {
        return (long) (Math.max(pageNum, 1) - 1) * pageSize;
    }
}

