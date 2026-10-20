package com.zhixue.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 课程推荐的门槛与上限。门槛均为余弦值，与 ai.rag.min-similarity 同一刻度 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai.recommend")
public class RecommendConfig {

    /** 课程得分（命中片段的最高余弦）门槛；高于回答检索的 0.45：资料可以有点相关，推荐必须明确相关 */
    private double courseMinCos = 0.55;
    /** 课时标题与问句的余弦门槛；达不到只显示课程卡片 */
    private double lessonMinCos = 0.50;
    private int maxCourses = 3;
    private int maxLessons = 2;
    /** 课时标题向量缓存条数 */
    private int lessonCacheSize = 1000;
}
