package com.zhixue.ai.chat;

import com.zhixue.ai.config.RecommendConfig;
import com.zhixue.ai.service.EmbeddingService;
import com.zhixue.api.course.domain.LessonOutline;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 课时标题向量的 LRU 缓存。键为「课时 ID + 标题」：标题改了键就变，旧向量自然不再命中，无需失效逻辑。
 */
@Component
public class LessonTitleVectors {

    private final EmbeddingService embeddingService;
    private final Map<String, List<Float>> cache;

    public LessonTitleVectors(EmbeddingService embeddingService, RecommendConfig config) {
        this.embeddingService = embeddingService;
        int capacity = Math.max(16, config.getLessonCacheSize());
        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<Float>> eldest) {
                return size() > capacity;
            }
        });
    }

    /**
     * 返回 课时 ID → 标题向量。未命中的标题一次批量向量化；向量化失败时抛出，由调用方降级为「不标课时」。
     */
    public Map<Long, List<Float>> vectorsOf(List<LessonOutline> lessons) {
        Map<Long, List<Float>> result = new HashMap<>();
        List<LessonOutline> misses = new ArrayList<>();
        for (LessonOutline lesson : lessons) {
            List<Float> cached = cache.get(key(lesson));
            if (cached != null) {
                result.put(lesson.getLessonId(), cached);
            } else {
                misses.add(lesson);
            }
        }
        if (misses.isEmpty()) {
            return result;
        }
        List<List<Float>> vectors = embeddingService.embedBatch(misses.stream().map(LessonOutline::getTitle).toList());
        if (vectors.size() != misses.size()) {
            // sandbox 下模型不可用返回空列表：不缓存，不标课时
            return result;
        }
        for (int i = 0; i < misses.size(); i++) {
            cache.put(key(misses.get(i)), vectors.get(i));
            result.put(misses.get(i).getLessonId(), vectors.get(i));
        }
        return result;
    }

    private static String key(LessonOutline lesson) {
        return lesson.getLessonId() + "|" + lesson.getTitle();
    }
}
