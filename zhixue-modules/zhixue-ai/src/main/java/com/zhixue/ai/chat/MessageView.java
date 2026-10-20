package com.zhixue.ai.chat;

import com.zhixue.ai.domain.entity.AiMessage;

import java.time.LocalDateTime;
import java.util.List;

/** 历史消息；recommendations 为打开历史时按最新状态解析出的卡片 */
public record MessageView(String id, String role, String content, int status, List<Source> sources,
                          List<RecommendationCard> recommendations, Integer costMs, LocalDateTime createTime) {

    public static MessageView of(AiMessage m, List<Source> sources, List<RecommendationCard> cards) {
        return new MessageView(String.valueOf(m.getId()), m.getRole(), m.getContent(),
                m.getStatus() == null ? MessageStatus.COMPLETED : m.getStatus(), sources, cards,
                m.getCostMs(), m.getCreateTime());
    }
}
