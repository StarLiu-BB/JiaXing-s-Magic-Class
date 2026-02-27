package com.zhixue.interaction.task;

import com.zhixue.interaction.service.LikeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 点赞数据同步定时任务。
 * 每隔 5 分钟将 Redis 中的点赞增量数据同步到数据库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LikeSyncTask {

    private final LikeService likeService;

    /**
     * 定时同步点赞数据。
     * 每隔 5 分钟执行一次。
     */
    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void syncLikes() {
        log.info("定时任务：开始同步点赞数据...");
        try {
            likeService.syncLikesToDatabase();
            log.info("定时任务：点赞数据同步完成");
        } catch (Exception e) {
            log.error("定时任务：点赞数据同步失败", e);
        }
    }
}
