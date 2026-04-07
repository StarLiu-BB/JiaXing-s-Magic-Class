package com.zhixue.course.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixue.common.mybatis.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 章节实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("chapter")
public class Chapter extends BaseEntity {

    private Long courseId;
    private String title;
    private Integer orderNum;
    @TableField(exist = false)
    private List<Section> lessons;

    public Integer getSortOrder() {
        return orderNum;
    }

    public void setSortOrder(Integer sortOrder) {
        this.orderNum = sortOrder;
    }
}
