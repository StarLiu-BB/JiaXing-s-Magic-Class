package com.zhixue.interaction.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class DanmakuExecutorConfig {
    @Bean(name = "danmakuAuditExecutor", destroyMethod = "shutdownNow")
    public ExecutorService danmakuAuditExecutor(@Value("${interaction.danmaku.workers:4}") int workers,
            @Value("${interaction.danmaku.queue-capacity:200}") int capacity) {
        if (workers < 1 || workers > 16 || capacity < 1 || capacity > 2000) {
            throw new IllegalArgumentException("审核线程数需1-16，队列容量需1-2000");
        }
        return new ThreadPoolExecutor(workers, workers, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(capacity),
                runnable -> new Thread(runnable, "danmaku-audit"), new ThreadPoolExecutor.AbortPolicy());
    }
}
