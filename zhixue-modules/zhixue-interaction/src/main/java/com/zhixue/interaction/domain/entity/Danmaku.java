package com.zhixue.interaction.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixue.common.mybatis.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 弹幕实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("danmaku")
public class Danmaku extends BaseEntity {

    /**
     * 关联课程/视频ID。
     */
    private Long roomId;

    /**
     * 发送用户ID。
     */
    private Long userId;

    /**
     * 弹幕内容。
     */
    private String content;

    /**
     * 发送时间（视频内时间点，秒）。
     */
    private Integer timePoint;

    /**
     * 0-待审核 1-通过 2-拒绝。
     */
    private Integer auditStatus;

    /**
     * 审核备注。
     */
    private String auditRemark;
}


