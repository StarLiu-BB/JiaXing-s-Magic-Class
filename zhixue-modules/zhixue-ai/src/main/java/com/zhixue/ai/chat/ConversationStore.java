package com.zhixue.ai.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.ai.domain.entity.AiConversation;
import com.zhixue.ai.domain.entity.AiMessage;
import com.zhixue.ai.mapper.AiConversationMapper;
import com.zhixue.ai.mapper.AiMessageMapper;
import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 会话与消息的持久化。所有读写都按用户归属校验：别人的会话与不存在的会话返回同样的 404，
 * 不暴露会话 ID 是否存在。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationStore {

    static final int TITLE_LENGTH = 20;
    static final int MAX_TITLE_LENGTH = 64;
    static final int MAX_PAGE_SIZE = 50;
    /** 取历史时最多回看的消息条数：足够凑出最近 3 轮已完成的问答 */
    private static final int HISTORY_SCAN = 20;
    private static final String USER = "user";
    private static final String ASSISTANT = "assistant";
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<StoredRecommendation>> STORED_LIST = new TypeReference<>() {
    };

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 一轮已完成的问答 */
    public record Turn(String question, String answer) {
    }

    public AiConversation requireOwned(Long conversationId, Long userId) {
        AiConversation conversation = conversationId == null ? null : conversationMapper.selectById(conversationId);
        if (conversation == null || !Objects.equals(conversation.getUserId(), userId)) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "会话不存在或已删除");
        }
        return conversation;
    }

    public AiConversation create(Long userId, String firstQuestion, Long courseId) {
        AiConversation conversation = new AiConversation();
        conversation.setUserId(userId);
        conversation.setTitle(titleOf(firstQuestion));
        conversation.setCourseId(courseId);
        conversation.setLastMessageTime(LocalDateTime.now());
        conversationMapper.insert(conversation);
        return conversation;
    }

    public AiMessage appendUserMessage(AiConversation conversation, String question) {
        AiMessage message = new AiMessage();
        message.setConversationId(conversation.getId());
        message.setUserId(conversation.getUserId());
        message.setRole(USER);
        message.setContent(question);
        message.setStatus(MessageStatus.COMPLETED);
        messageMapper.insert(message);
        touch(conversation.getId());
        return message;
    }

    /**
     * @param messageId 生成开始时预先分配的 ID（meta 事件已经把它发给了前端）
     */
    public void saveAssistantMessage(long messageId, AiConversation conversation, String content, int status,
                                     List<Source> sources, List<StoredRecommendation> recommendations, long costMs) {
        AiMessage message = new AiMessage();
        message.setId(messageId);
        message.setConversationId(conversation.getId());
        message.setUserId(conversation.getUserId());
        message.setRole(ASSISTANT);
        message.setContent(content == null ? "" : content);
        message.setStatus(status);
        message.setSources(toJson(sources == null ? List.of() : sources.stream().map(Source::title).toList()));
        message.setRecommendations(toJson(recommendations == null ? List.of() : recommendations));
        message.setCostMs((int) Math.min(costMs, Integer.MAX_VALUE));
        messageMapper.insert(message);
        touch(conversation.getId());
    }

    /** 最近 rounds 轮已完成的问答（回答失败或已停止的那一轮不带入），按时间正序 */
    public List<Turn> completedHistory(Long conversationId, Long beforeMessageId, int rounds) {
        List<AiMessage> recent = new ArrayList<>(messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId)
                .lt(AiMessage::getId, beforeMessageId)
                .orderByDesc(AiMessage::getId)
                .last("LIMIT " + HISTORY_SCAN)));
        Collections.reverse(recent);
        List<Turn> turns = new ArrayList<>();
        for (int i = 0; i + 1 < recent.size(); i++) {
            AiMessage question = recent.get(i);
            AiMessage answer = recent.get(i + 1);
            if (USER.equals(question.getRole()) && ASSISTANT.equals(answer.getRole())
                    && Objects.equals(answer.getStatus(), MessageStatus.COMPLETED)) {
                turns.add(new Turn(question.getContent(), answer.getContent()));
                i++;
            }
        }
        return turns.size() <= rounds ? turns : turns.subList(turns.size() - rounds, turns.size());
    }

    /** 同一会话中上一个学生问题（无论回答成败），用于理解「那它呢」这类追问 */
    public String previousQuestion(Long conversationId, Long beforeMessageId) {
        List<AiMessage> last = messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId)
                .eq(AiMessage::getRole, USER)
                .lt(AiMessage::getId, beforeMessageId)
                .orderByDesc(AiMessage::getId)
                .last("LIMIT 1"));
        return last.isEmpty() ? null : last.get(0).getContent();
    }

    public PageResult<ConversationView> listRecent(Long userId, int pageNum, int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        Page<AiConversation> page = conversationMapper.selectPage(new Page<>(Math.max(pageNum, 1), size),
                new LambdaQueryWrapper<AiConversation>()
                        .eq(AiConversation::getUserId, userId)
                        .orderByDesc(AiConversation::getLastMessageTime)
                        .orderByDesc(AiConversation::getId));
        return PageResult.of(page.getRecords().stream().map(ConversationView::of).toList(), page.getTotal(), size);
    }

    public List<AiMessage> messagesOf(Long conversationId) {
        return messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId)
                .orderByAsc(AiMessage::getId));
    }

    public void rename(Long conversationId, Long userId, String title) {
        String trimmed = title == null ? "" : title.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_TITLE_LENGTH) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "标题长度为 1-" + MAX_TITLE_LENGTH + " 个字");
        }
        requireOwned(conversationId, userId);
        AiConversation update = new AiConversation();
        update.setId(conversationId);
        update.setTitle(trimmed);
        conversationMapper.updateById(update);
    }

    /** 逻辑删除会话；消息随会话一起不可见 */
    public void delete(Long conversationId, Long userId) {
        requireOwned(conversationId, userId);
        conversationMapper.deleteById(conversationId);
    }

    public List<Source> sourcesOf(AiMessage message) {
        return parse(message.getSources(), STRING_LIST).stream().map(Source::new).toList();
    }

    public List<StoredRecommendation> recommendationsOf(AiMessage message) {
        return parse(message.getRecommendations(), STORED_LIST);
    }

    /** 首个问题的前 20 个字（合并空白）；全是空白时为「新对话」 */
    static String titleOf(String question) {
        String normalized = question == null ? "" : question.strip().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return "新对话";
        }
        int codePoints = normalized.codePointCount(0, normalized.length());
        return normalized.substring(0, normalized.offsetByCodePoints(0, Math.min(TITLE_LENGTH, codePoints)));
    }

    private void touch(Long conversationId) {
        AiConversation update = new AiConversation();
        update.setId(conversationId);
        update.setLastMessageTime(LocalDateTime.now());
        conversationMapper.updateById(update);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("序列化失败", e);
        }
    }

    private <T> List<T> parse(String json, TypeReference<List<T>> type) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<T> value = objectMapper.readValue(json, type);
            return value == null ? List.of() : value;
        } catch (Exception e) {
            log.warn("消息附加数据无法解析，按空处理：{}", e.getMessage());
            return List.of();
        }
    }
}
