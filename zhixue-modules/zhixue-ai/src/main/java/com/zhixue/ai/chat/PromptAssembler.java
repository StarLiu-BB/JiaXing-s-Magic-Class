package com.zhixue.ai.chat;

import com.zhixue.ai.controller.AiChatController;
import com.zhixue.ai.domain.entity.PromptTemplate;
import com.zhixue.ai.service.KnowledgeBaseService.RetrievedChunk;
import com.zhixue.ai.service.LlmService.ChatMessage;
import com.zhixue.ai.service.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/** 系统指令 + 最近 3 轮问答 + 本轮【课程资料】与【问题】 */
@Component
@RequiredArgsConstructor
public class PromptAssembler {

    public static final int HISTORY_ROUNDS = 3;
    static final int MAX_HISTORY_CHARS = 800;
    static final int CONTEXT_CHUNKS = 4;

    private final PromptTemplateService promptTemplateService;

    public List<ChatMessage> build(List<ConversationStore.Turn> history, List<RetrievedChunk> context, String question) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", systemPrompt()));
        List<ConversationStore.Turn> recent = history.size() <= HISTORY_ROUNDS ? history
                : history.subList(history.size() - HISTORY_ROUNDS, history.size());
        for (ConversationStore.Turn turn : recent) {
            messages.add(new ChatMessage("user", truncate(turn.question())));
            messages.add(new ChatMessage("assistant", truncate(turn.answer())));
        }
        messages.add(new ChatMessage("user", userPrompt(context, question)));
        return messages;
    }

    /** 进提示词的资料：检索命中的前 4 条（其余 4 条只参与推荐） */
    public List<RetrievedChunk> contextOf(List<RetrievedChunk> hits) {
        return hits.stream().limit(CONTEXT_CHUNKS).toList();
    }

    /** 检索用的问句：带上一个问题，才能理解「那它和 HashMap 有什么区别」；只带上一问，避免话题漂移 */
    public static String retrievalQuery(String question, String previousQuestion) {
        return StringUtils.hasText(previousQuestion) ? question + "\n" + previousQuestion : question;
    }

    private String systemPrompt() {
        PromptTemplate template = promptTemplateService.getByCode("COURSE_QA");
        if (template == null || !StringUtils.hasText(template.getContent())) {
            return AiChatController.DEFAULT_SYSTEM_PROMPT;
        }
        return template.getContent().replace("{{context}}", "").replace("{{question}}", "").trim();
    }

    private static String userPrompt(List<RetrievedChunk> context, String question) {
        StringBuilder sb = new StringBuilder("【课程资料】\n");
        if (context.isEmpty()) {
            sb.append("（未检索到相关资料）\n");
        } else {
            for (int i = 0; i < context.size(); i++) {
                RetrievedChunk chunk = context.get(i);
                sb.append(i + 1).append(". 《").append(chunk.title()).append("》").append(chunk.content()).append('\n');
            }
        }
        return sb.append("\n【问题】\n").append(question).toString();
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= MAX_HISTORY_CHARS ? text : text.substring(0, MAX_HISTORY_CHARS) + "…";
    }
}
