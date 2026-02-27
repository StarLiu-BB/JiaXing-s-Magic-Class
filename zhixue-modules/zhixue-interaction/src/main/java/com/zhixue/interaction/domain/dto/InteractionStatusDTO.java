package com.zhixue.interaction.domain.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 课程互动状态 DTO。
 */
@Data
public class InteractionStatusDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 课程ID。
     */
    private Long courseId;

    /**
     * 当前用户是否已点赞。
     */
    private boolean liked;

    /**
     * 当前用户是否已收藏。
     */
    private boolean favorited;

    /**
     * 课程点赞总数。
     */
    private long likeCount;

    /**
     * 课程收藏总数。
     */
    private long favoriteCount;
}
