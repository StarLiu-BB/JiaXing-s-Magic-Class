package com.zhixue.interaction.controller;

import com.zhixue.common.core.domain.PageQuery;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.domain.R;
import com.zhixue.interaction.domain.dto.InteractionStatusDTO;
import com.zhixue.interaction.domain.entity.CourseFavorite;
import com.zhixue.interaction.service.FavoriteService;
import com.zhixue.interaction.service.LikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 课程互动接口（点赞、收藏）。
 */
@RestController
@RequestMapping("/course/interaction")
@RequiredArgsConstructor
public class CourseInteractionController {

    private final LikeService likeService;
    private final FavoriteService favoriteService;

    /**
     * 点赞/取消点赞课程。
     *
     * @param courseId 课程ID
     * @return true-点赞成功，false-取消点赞成功
     */
    @PostMapping("/like/{courseId}")
    public R<Boolean> toggleLike(@PathVariable Long courseId) {
        boolean liked = likeService.toggleLike(courseId);
        return R.ok(liked);
    }

    /**
     * 收藏/取消收藏课程。
     *
     * @param courseId 课程ID
     * @return true-收藏成功，false-取消收藏成功
     */
    @PostMapping("/favorite/{courseId}")
    public R<Boolean> toggleFavorite(@PathVariable Long courseId) {
        boolean favorited = favoriteService.toggleFavorite(courseId);
        return R.ok(favorited);
    }

    /**
     * 查询当前用户对某课程的互动状态（是否点赞、是否收藏）。
     *
     * @param courseId 课程ID
     * @return 互动状态
     */
    @GetMapping("/status/{courseId}")
    public R<InteractionStatusDTO> getInteractionStatus(@PathVariable Long courseId) {
        InteractionStatusDTO dto = new InteractionStatusDTO();
        dto.setCourseId(courseId);
        dto.setLiked(likeService.isLiked(courseId));
        dto.setFavorited(favoriteService.isFavorited(courseId));
        dto.setLikeCount(likeService.getLikeCount(courseId));
        dto.setFavoriteCount(favoriteService.getFavoriteCount(courseId));
        return R.ok(dto);
    }

    /**
     * 获取课程的点赞数。
     *
     * @param courseId 课程ID
     * @return 点赞数
     */
    @GetMapping("/like/count/{courseId}")
    public R<Long> getLikeCount(@PathVariable Long courseId) {
        return R.ok(likeService.getLikeCount(courseId));
    }

    /**
     * 获取课程的收藏数。
     *
     * @param courseId 课程ID
     * @return 收藏数
     */
    @GetMapping("/favorite/count/{courseId}")
    public R<Long> getFavoriteCount(@PathVariable Long courseId) {
        return R.ok(favoriteService.getFavoriteCount(courseId));
    }

    /**
     * 分页查询当前用户的收藏列表。
     *
     * @param query 分页参数
     * @return 收藏列表
     */
    @GetMapping("/favorites")
    public R<PageResult<CourseFavorite>> getUserFavorites(PageQuery query) {
        return R.ok(favoriteService.getUserFavorites(query.getPageNum(), query.getPageSize()));
    }
}
