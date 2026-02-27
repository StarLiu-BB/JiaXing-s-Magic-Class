package com.zhixue.interaction.service.impl;

import com.zhixue.common.security.context.SecurityContextHolder;
import com.zhixue.interaction.domain.entity.CourseLike;
import com.zhixue.interaction.domain.entity.CourseStats;
import com.zhixue.interaction.mapper.CourseLikeMapper;
import com.zhixue.interaction.mapper.CourseStatsMapper;
import com.zhixue.interaction.service.LikeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 课程点赞服务实现类。
 * 采用 Redis 缓存优先策略，通过定时任务同步到数据库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LikeServiceImpl implements LikeService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CourseLikeMapper courseLikeMapper;
    private final CourseStatsMapper courseStatsMapper;

    /**
     * Redis Key 前缀：存储课程的点赞用户集合。
     * 格式：like:course:{courseId} -> Set<userId>
     */
    private static final String LIKE_KEY_PREFIX = "like:course:";

    /**
     * Redis Key：记录有点赞变更的课程ID集合。
     */
    private static final String LIKE_CHANGED_COURSES_KEY = "like:changed:courses";

    /**
     * Redis Key 前缀：存储课程点赞数增量。
     * 格式：like:count:delta:{courseId} -> Integer
     */
    private static final String LIKE_COUNT_DELTA_PREFIX = "like:count:delta:";

    @Override
    public boolean toggleLike(Long courseId) {
        Long userId = getCurrentUserId();
        String likeKey = LIKE_KEY_PREFIX + courseId;

        // 检查是否已点赞
        Boolean isMember = redisTemplate.opsForSet().isMember(likeKey, userId);
        if (Boolean.TRUE.equals(isMember)) {
            // 取消点赞
            redisTemplate.opsForSet().remove(likeKey, userId);
            // 记录增量 -1
            redisTemplate.opsForValue().increment(LIKE_COUNT_DELTA_PREFIX + courseId, -1);
            // 标记该课程有变更
            redisTemplate.opsForSet().add(LIKE_CHANGED_COURSES_KEY, courseId);
            log.info("用户 {} 取消点赞课程 {}", userId, courseId);
            return false;
        } else {
            // 点赞
            redisTemplate.opsForSet().add(likeKey, userId);
            // 记录增量 +1
            redisTemplate.opsForValue().increment(LIKE_COUNT_DELTA_PREFIX + courseId, 1);
            // 标记该课程有变更
            redisTemplate.opsForSet().add(LIKE_CHANGED_COURSES_KEY, courseId);
            log.info("用户 {} 点赞课程 {}", userId, courseId);
            return true;
        }
    }

    @Override
    public boolean isLiked(Long courseId) {
        Long userId = getCurrentUserId();
        String likeKey = LIKE_KEY_PREFIX + courseId;
        Boolean isMember = redisTemplate.opsForSet().isMember(likeKey, userId);
        return Boolean.TRUE.equals(isMember);
    }

    @Override
    public long getLikeCount(Long courseId) {
        String likeKey = LIKE_KEY_PREFIX + courseId;
        Long count = redisTemplate.opsForSet().size(likeKey);
        return count != null ? count : 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncLikesToDatabase() {
        log.info("开始同步点赞数据到数据库...");

        // 获取所有有变更的课程ID
        Set<Object> changedCourses = redisTemplate.opsForSet().members(LIKE_CHANGED_COURSES_KEY);
        if (changedCourses == null || changedCourses.isEmpty()) {
            log.info("没有需要同步的点赞数据");
            return;
        }

        for (Object courseIdObj : changedCourses) {
            Long courseId = Long.valueOf(courseIdObj.toString());
            try {
                syncCourseLikes(courseId);
            } catch (Exception e) {
                log.error("同步课程 {} 点赞数据失败", courseId, e);
            }
        }

        // 清空变更记录
        redisTemplate.delete(LIKE_CHANGED_COURSES_KEY);
        log.info("点赞数据同步完成，共处理 {} 个课程", changedCourses.size());
    }

    /**
     * 同步单个课程的点赞数据。
     */
    private void syncCourseLikes(Long courseId) {
        String deltaKey = LIKE_COUNT_DELTA_PREFIX + courseId;
        String likeKey = LIKE_KEY_PREFIX + courseId;

        // 获取增量
        Object deltaObj = redisTemplate.opsForValue().get(deltaKey);
        int delta = deltaObj != null ? Integer.parseInt(deltaObj.toString()) : 0;

        if (delta == 0) {
            return;
        }

        // 更新统计表
        int updated = courseStatsMapper.incrementLikeCount(courseId, delta);
        if (updated == 0) {
            // 不存在则创建
            CourseStats stats = new CourseStats();
            stats.setCourseId(courseId);
            stats.setLikeCount(Math.max(0, delta));
            stats.setFavoriteCount(0);
            courseStatsMapper.insert(stats);
        }

        // 同步点赞记录到数据库
        Set<Object> likedUsers = redisTemplate.opsForSet().members(likeKey);
        if (likedUsers != null) {
            for (Object userIdObj : likedUsers) {
                Long userId = Long.valueOf(userIdObj.toString());
                syncUserLike(userId, courseId, true);
            }
        }

        // 清除增量记录
        redisTemplate.delete(deltaKey);
        log.debug("课程 {} 点赞数据同步完成，增量: {}", courseId, delta);
    }

    /**
     * 同步用户点赞记录。
     */
    private void syncUserLike(Long userId, Long courseId, boolean liked) {
        CourseLike existing = courseLikeMapper.selectByUserAndCourseIncludeDeleted(userId, courseId);

        if (existing == null && liked) {
            // 新增点赞记录
            CourseLike like = new CourseLike();
            like.setUserId(userId);
            like.setCourseId(courseId);
            courseLikeMapper.insert(like);
        } else if (existing != null) {
            // 更新逻辑删除状态
            existing.setDeleted(liked ? 0 : 1);
            existing.setUpdateTime(LocalDateTime.now());
            courseLikeMapper.updateById(existing);
        }
    }

    /**
     * 获取当前登录用户ID。
     */
    private Long getCurrentUserId() {
        return SecurityContextHolder.getLoginUser().getUserId();
    }
}
