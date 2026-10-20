package com.zhixue.ai.chat;

import java.util.List;

/**
 * 落库形态：只有课程/课时 ID 与分数。价格、是否已购会变化，打开历史时按最新状态重新解析。
 */
public record StoredRecommendation(String courseId, double score, List<String> lessonIds) {
}
