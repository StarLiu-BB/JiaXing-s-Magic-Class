package com.zhixue.course.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixue.common.mybatis.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 小节实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("section")
public class Section extends BaseEntity {

    private Long courseId;
    private Long chapterId;
    private String title;
    private String videoUrl;
    private Integer duration; // 秒
    private Integer orderNum;
    private Integer status; // 0未发布 1已发布

    /** 是否为试看课时（按课程试看节数计算，不落库）。 */
    @TableField(exist = false)
    private Boolean preview;

    /** 当前用户无权观看时为 true，此时 videoUrl 不下发（不落库）。 */
    @TableField(exist = false)
    private Boolean locked;

    public Integer getSortOrder() {
        return orderNum;
    }

    public void setSortOrder(Integer sortOrder) {
        this.orderNum = sortOrder;
    }
}
