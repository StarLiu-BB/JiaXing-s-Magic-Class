package com.zhixue.interaction.service;

import com.zhixue.common.core.domain.PageResult;
import com.zhixue.interaction.domain.entity.CourseFavorite;

/**
 * 课程收藏服务接口。
 */
public interface FavoriteService {

    /**
     * 收藏/取消收藏（Toggle 模式）。
     *
     * @param courseId 课程ID
     * @return true-收藏成功，false-取消收藏成功
     */
    boolean toggleFavorite(Long courseId);

    /**
     * 查询用户是否已收藏某课程。
     *
     * @param courseId 课程ID
     * @return true-已收藏
     */
    boolean isFavorited(Long courseId);

    /**
     * 获取课程的收藏总数。
     *
     * @param courseId 课程ID
     * @return 收藏数
     */
    long getFavoriteCount(Long courseId);

    /**
     * 分页查询用户的收藏列表。
     *
     * @param pageNum  页码
     * @param pageSize 每页数量
     * @return 收藏列表
     */
    PageResult<CourseFavorite> getUserFavorites(int pageNum, int pageSize);
}
