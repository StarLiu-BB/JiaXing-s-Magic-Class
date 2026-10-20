package com.zhixue.ai.chat;

import com.zhixue.ai.controller.AiChatController;
import com.zhixue.ai.domain.entity.PromptTemplate;
import com.zhixue.ai.service.KnowledgeBaseService.RetrievedChunk;
import com.zhixue.ai.service.LlmService.ChatMessage;
import com.zhixue.ai.service.PromptTemplateService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromptAssemblerTest {

    private final PromptTemplateService templates = mock(PromptTemplateService.class);
    private final PromptAssembler assembler = new PromptAssembler(templates);

    @Test
    void keepsLastThreeTurnsTruncatedAndPutsMaterialsBeforeTheQuestion() {
        List<ConversationStore.Turn> history = List.of(
                new ConversationStore.Turn("Q1", "A1"), new ConversationStore.Turn("Q2", "A2"),
                new ConversationStore.Turn("Q3", "长".repeat(900)), new ConversationStore.Turn("Q4", "A4"));
        List<RetrievedChunk> context = List.of(new RetrievedChunk(92017L, 2007L, 0.66,
                "Java 并发编程实战 · 锁与并发容器", "JDK 8 的 ConcurrentHashMap 对非空桶只锁头节点"));

        List<ChatMessage> messages = assembler.build(history, context, "那 ConcurrentHashMap 呢");

        assertThat(messages).extracting(ChatMessage::role)
                .containsExactly("system", "user", "assistant", "user", "assistant", "user", "assistant", "user");
        assertThat(messages.get(1).content()).isEqualTo("Q2"); // Q1 超出 3 轮
        assertThat(messages.get(4).content()).hasSize(801).endsWith("…"); // 800 字 + 省略号
        String last = messages.get(7).content();
        assertThat(last).contains("1. 《Java 并发编程实战 · 锁与并发容器》JDK 8 的 ConcurrentHashMap");
        assertThat(last.indexOf("【课程资料】")).isLessThan(last.indexOf("【问题】"));
        assertThat(last).endsWith("那 ConcurrentHashMap 呢");
    }

    @Test
    void saysSoWhenNothingWasRetrieved() {
        List<ChatMessage> messages = assembler.build(List.of(), List.of(), "今天中午吃什么");
        assertThat(messages.get(messages.size() - 1).content()).contains("（未检索到相关资料）");
    }

    @Test
    void onlyTopFourChunksGoIntoThePrompt() {
        List<RetrievedChunk> hits = List.of(1L, 2L, 3L, 4L, 5L, 6L).stream()
                .map(id -> new RetrievedChunk(id, 2007L, 0.6, "资料 " + id, "内容")).toList();
        assertThat(assembler.contextOf(hits)).extracting(RetrievedChunk::chunkId).containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    void retrievalQueryAppendsPreviousQuestionForFollowUps() {
        assertThat(PromptAssembler.retrievalQuery("那它呢", "HashMap 为什么线程不安全"))
                .isEqualTo("那它呢\nHashMap 为什么线程不安全");
        assertThat(PromptAssembler.retrievalQuery("HashMap 为什么线程不安全", null))
                .isEqualTo("HashMap 为什么线程不安全");
    }

    @Test
    void systemPromptComesFromTemplateWithPlaceholdersRemoved() {
        assertThat(assembler.build(List.of(), List.of(), "q").get(0).content())
                .isEqualTo(AiChatController.DEFAULT_SYSTEM_PROMPT);
        PromptTemplate template = new PromptTemplate();
        template.setContent("你是助教。{{context}}{{question}}");
        when(templates.getByCode("COURSE_QA")).thenReturn(template);
        assertThat(assembler.build(List.of(), List.of(), "q").get(0).content()).isEqualTo("你是助教。");
    }
}
