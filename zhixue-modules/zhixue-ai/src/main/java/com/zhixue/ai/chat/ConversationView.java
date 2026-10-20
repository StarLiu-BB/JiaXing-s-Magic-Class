package com.zhixue.ai.chat;

import com.zhixue.ai.domain.entity.AiConversation;

import java.time.LocalDateTime;

/** 「最近」列表的一项；ID 为字符串 */
public record ConversationView(String id, String title, String courseId, LocalDateTime lastMessageTime) {

    /** 控制器在另一个包，必须是 public */
    public static ConversationView of(AiConversation c) {
        return new ConversationView(String.valueOf(c.getId()), c.getTitle(),
                c.getCourseId() == null ? null : String.valueOf(c.getCourseId()), c.getLastMessageTime());
    }
}
