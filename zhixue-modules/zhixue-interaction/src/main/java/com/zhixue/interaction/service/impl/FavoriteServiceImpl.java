package com.zhixue.interaction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.redis.service.RedisService;
import com.zhixue.common.security.context.SecurityContextHolder;
import com.zhixue.interaction.domain.entity.CourseFavorite;
import com.zhixue.interaction.domain.entity.CourseStats;
import com.zhixue.interaction.mapper.CourseFavoriteMapper;
import com.zhixue.interaction.mapper.CourseStatsMapper;
import com.zhixue.interaction.service.FavoriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 课程收藏服务实现类。
 * 收藏属于重要资产数据，直接操作数据库，查询时结合 Redis 缓存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final CourseFavoriteMapper courseFavoriteMapper;
    private final CourseStatsMapper courseStatsMapper;
    private final RedisService redisService;

    /**
     * Redis Key 前缀：用户收藏状态缓存。
     * 格式：favorite:user:{userId}:course:{courseId} -> Boolean
     */
    private static final String FAVORITE_STATUS_PREFIX = "favorite:user:";

    /**
     * Redis Key 前缀：课程收藏数缓存。
     * 格式：favorite:count:{courseId} -> Integer
     */
    private static final String FAVORITE_COUNT_PREFIX = "favorite:count:";

    /**
     * 缓存过期时间（小时）。
     */
    private static final int CACHE_EXPIRE_HOURS = 24;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleFavorite(Long courseId) {
        Long userId = getCurrentUserId();
        String statusKey = buildStatusKey(userId, courseId);

        // 查询是否已收藏（包含已删除记录）
        CourseFavorite existing = courseFavoriteMapper.selectByUserAndCourseIncludeDeleted(userId, courseId);

        boolean favorited;
        if (existing == null) {
            // 新增收藏记录
            CourseFavorite favorite = new CourseFavorite();
            favorite.setUserId(userId);
            favorite.setCourseId(courseId);
            courseFavoriteMapper.insert(favorite);
            favorited = true;
            updateFavoriteCount(courseId, 1);
            log.info("用户 {} 收藏课程 {}", userId, courseId);
        } else if (existing.getDeleted() == 0) {
            // 取消收藏（逻辑删除）
            existing.setDeleted(1);
            existing.setUpdateTime(LocalDateTime.now());
            courseFavoriteMapper.updateById(existing);
            favorited = false;
            updateFavoriteCount(courseId, -1);
            log.info("用户 {} 取消收藏课程 {}", userId, courseId);
        } else {
            // 恢复收藏
            existing.setDeleted(0);
            existing.setUpdateTime(LocalDateTime.now());
            courseFavoriteMapper.updateById(existing);
            favorited = true;
            updateFavoriteCount(courseId, 1);
            log.info("用户 {} 恢复收藏课程 {}", userId, courseId);
        }

        // 更新缓存
        redisService.set(statusKey, favorited, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
        // 清除收藏数缓存
        redisService.delete(FAVORITE_COUNT_PREFIX + courseId);

        return favorited;
    }

    @Override
    public boolean isFavorited(Long courseId) {
        Long userId = getCurrentUserId();
        String statusKey = buildStatusKey(userId, courseId);

        // 先查缓存
        Boolean cached = redisService.get(statusKey);
        if (cached != null) {
            return cached;
        }

        // 查数据库
        LambdaQueryWrapper<CourseFavorite> qw = new LambdaQueryWrapper<>();
        qw.eq(CourseFavorite::getUserId, userId)
          .eq(CourseFavorite::getCourseId, courseId);
        boolean exists = courseFavoriteMapper.selectCount(qw) > 0;

        // 写入缓存
        redisService.set(statusKey, exists, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);

        return exists;
    }

    @Override
    public long getFavoriteCount(Long courseId) {
        String countKey = FAVORITE_COUNT_PREFIX + courseId;

        // 先查缓存
        Long cached = redisService.get(countKey);
        if (cached != null) {
            return cached;
        }

        // 查数据库
        LambdaQueryWrapper<CourseStats> qw = new LambdaQueryWrapper<>();
        qw.eq(CourseStats::getCourseId, courseId);
        CourseStats stats = courseStatsMapper.selectOne(qw);
        long count = stats != null ? stats.getFavoriteCount() : 0;

        // 写入缓存
        redisService.set(countKey, count, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);

        return count;
    }

    @Override
    public PageResult<CourseFavorite> getUserFavorites(int pageNum, int pageSize) {
        Long userId = getCurrentUserId();

        LambdaQueryWrapper<CourseFavorite> qw = new LambdaQueryWrapper<>();
        qw.eq(CourseFavorite::getUserId, userId)
          .orderByDesc(CourseFavorite::getCreateTime);

        Page<CourseFavorite> page = courseFavoriteMapper.selectPage(
                new Page<>(pageNum, pageSize), qw);

        return PageResult.of(page.getRecords(), page.getTotal(), page.getSize());
    }

    /**
     * 更新收藏数统计。
     */
    private void updateFavoriteCount(Long courseId, int delta) {
        int updated = courseStatsMapper.incrementFavoriteCount(courseId, delta);
        if (updated == 0) {
            // 不存在则创建
            CourseStats stats = new CourseStats();
            stats.setCourseId(courseId);
            stats.setLikeCount(0);
            stats.setFavoriteCount(Math.max(0, delta));
            courseStatsMapper.insert(stats);
        }
    }

    /**
     * 构建收藏状态缓存Key。
     */
    private String buildStatusKey(Long userId, Long courseId) {
        return FAVORITE_STATUS_PREFIX + userId + ":course:" + courseId;
    }

    /**
     * 获取当前登录用户ID。
     */
    private Long getCurrentUserId() {
        return SecurityContextHolder.getLoginUser().getUserId();
    }
}
