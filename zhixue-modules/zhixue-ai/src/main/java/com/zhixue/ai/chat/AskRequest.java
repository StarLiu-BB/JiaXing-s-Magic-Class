package com.zhixue.ai.chat;

/**
 * 提问请求。ID 在 JSON 里是字符串，Jackson 会转成 Long。
 *
 * @param conversationId 为空表示新建会话
 * @param courseId       限定课程提问；只在新建会话时生效，已有会话以会话保存的范围为准
 */
public record AskRequest(Long conversationId, String question, Long courseId) {
}
