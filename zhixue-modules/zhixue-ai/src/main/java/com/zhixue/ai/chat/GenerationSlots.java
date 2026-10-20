package com.zhixue.ai.chat;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「同一用户同时只能有一个回答在生成」的占位。本地模型只有一块 GPU，排队会相互拖慢。
 *
 * <p>只在单个 AI 服务实例的内存中有效；多实例部署前须改为 Redis SET NX + 过期时间（设计文档 9 节）。</p>
 */
@Component
public class GenerationSlots {

    private final Set<Long> busy = ConcurrentHashMap.newKeySet();

    /** 原子占位；已有进行中的回答时返回 false */
    public boolean tryAcquire(Long userId) {
        return busy.add(userId);
    }

    public void release(Long userId) {
        busy.remove(userId);
    }
}
