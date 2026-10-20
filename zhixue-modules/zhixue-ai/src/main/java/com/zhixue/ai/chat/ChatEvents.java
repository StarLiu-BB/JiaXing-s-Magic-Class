package com.zhixue.ai.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.ai.domain.entity.AiConversation;
import org.springframework.http.codec.ServerSentEvent;

import java.util.List;

/** SSE 事件载荷。由这里自行序列化成 JSON 字符串，保证 ID 一律为字符串 */
final class ChatEvents {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    record Meta(String conversationId, String messageId, String title) {
    }

    record Delta(String text) {
    }

    record Done(String messageId, List<Source> sources, String notice, List<RecommendationCard> recommendations) {
    }

    record Failure(int code, String msg) {
    }

    private ChatEvents() {
    }

    static ServerSentEvent<String> meta(AiConversation conversation, long messageId) {
        return sse("meta", new Meta(String.valueOf(conversation.getId()), String.valueOf(messageId),
                conversation.getTitle()));
    }

    static ServerSentEvent<String> delta(String text) {
        return sse("delta", new Delta(text));
    }

    static ServerSentEvent<String> done(long messageId, List<Source> sources, String notice,
                                        List<RecommendationCard> recommendations) {
        return sse("done", new Done(String.valueOf(messageId), sources, notice, recommendations));
    }

    static ServerSentEvent<String> failure(int code, String msg) {
        return sse("error", new Failure(code, msg));
    }

    private static ServerSentEvent<String> sse(String event, Object payload) {
        try {
            return ServerSentEvent.builder(MAPPER.writeValueAsString(payload)).event(event).build();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("SSE 事件序列化失败", e);
        }
    }
}
