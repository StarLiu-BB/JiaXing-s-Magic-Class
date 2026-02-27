package com.zhixue.interaction.service;

/**
 * 课程点赞服务接口。
 */
public interface LikeService {

    /**
     * 点赞/取消点赞（Toggle 模式）。
     *
     * @param courseId 课程ID
     * @return true-点赞成功，false-取消点赞成功
     */
    boolean toggleLike(Long courseId);

    /**
     * 查询用户是否已点赞某课程。
     *
     * @param courseId 课程ID
     * @return true-已点赞
     */
    boolean isLiked(Long courseId);

    /**
     * 获取课程的点赞总数。
     *
     * @param courseId 课程ID
     * @return 点赞数
     */
    long getLikeCount(Long courseId);

    /**
     * 同步 Redis 点赞数据到数据库。
     */
    void syncLikesToDatabase();
}
