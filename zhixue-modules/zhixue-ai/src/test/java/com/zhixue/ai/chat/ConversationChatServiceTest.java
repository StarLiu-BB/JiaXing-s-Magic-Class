package com.zhixue.ai.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.ai.domain.entity.AiConversation;
import com.zhixue.ai.domain.entity.AiMessage;
import com.zhixue.ai.service.EmbeddingService;
import com.zhixue.ai.service.KnowledgeBaseService;
import com.zhixue.ai.service.KnowledgeBaseService.RetrievedChunk;
import com.zhixue.ai.service.LlmService;
import com.zhixue.ai.service.LlmService.ChatMessage;
import com.zhixue.ai.service.PromptTemplateService;
import com.zhixue.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 一次提问的完整编排。重点是边界：
 * 占位必须先于任何写库（409 不留垃圾）、流前异常释放占位、流开始后三种结束方式都落库并释放占位。
 */
class ConversationChatServiceTest {

    private static final Viewer STUDENT = new Viewer(1003L, false);
    private static final String QUESTION = "HashMap 为什么线程不安全";

    private final ConversationStore store = mock(ConversationStore.class);
    private final GenerationSlots slots = new GenerationSlots();
    private final EmbeddingService embedding = mock(EmbeddingService.class);
    private final KnowledgeBaseService knowledge = mock(KnowledgeBaseService.class);
    private final LlmService llm = mock(LlmService.class);
    private final CourseRecommender recommender = mock(CourseRecommender.class);
    private final ObjectMapper json = new ObjectMapper();
    private final AiConversation conversation = new AiConversation();
    private ConversationChatService service;

    @BeforeEach
    void setUp() {
        service = new ConversationChatService(store, slots, new PromptAssembler(mock(PromptTemplateService.class)),
                embedding, knowledge, llm, recommender);
        conversation.setId(100L);
        conversation.setUserId(1003L);
        conversation.setTitle(QUESTION);
        AiMessage question = new AiMessage();
        question.setId(101L);
        when(store.create(eq(1003L), anyString(), any())).thenReturn(conversation);
        when(store.appendUserMessage(eq(conversation), anyString())).thenReturn(question);
        when(store.completedHistory(anyLong(), anyLong(), anyInt())).thenReturn(List.of());
        when(embedding.embed(anyString())).thenReturn(List.of(1f, 0f));
        when(knowledge.retrieveScored(anyString(), anyList(), anyInt(), any())).thenReturn(List.of(new RetrievedChunk(
                92017L, 2007L, 0.66, "Java 并发编程实战 · 锁与并发容器", "ConcurrentHashMap 只锁桶的头节点")));
        when(recommender.recommend(anyList(), anyList(), eq(STUDENT))).thenReturn(new CourseRecommender.Result(
                List.of(new StoredRecommendation("2007", 0.66, List.of())),
                List.of(new RecommendationCard("2007", "Java 并发编程实战", "c.png", new BigDecimal("99.00"),
                        false, true, List.of()))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void streamsMetaThenDeltasThenDoneAndSavesTheAnswer() throws Exception {
        when(llm.chatStream(anyList())).thenReturn(Flux.just("Hash", "Map"));

        List<ServerSentEvent<String>> events = service.ask(STUDENT, new AskRequest(null, QUESTION, null))
                .collectList().block(Duration.ofSeconds(5));

        assertThat(events).extracting(ServerSentEvent::event).containsExactly("meta", "delta", "delta", "done");
        JsonNode meta = json.readTree(events.get(0).data());
        JsonNode done = json.readTree(events.get(3).data());
        assertThat(meta.path("conversationId").asText()).isEqualTo("100");
        assertThat(done.path("messageId").asText()).isEqualTo(meta.path("messageId").asText());
        assertThat(done.path("sources").get(0).path("title").asText()).isEqualTo("Java 并发编程实战 · 锁与并发容器");
        assertThat(done.path("recommendations").get(0).path("courseId").asText()).isEqualTo("2007");
        assertThat(done.path("notice").isNull()).isTrue();
        verify(store).saveAssistantMessage(eq(Long.parseLong(meta.path("messageId").asText())), eq(conversation),
                eq("HashMap"), eq(MessageStatus.COMPLETED), anyList(),
                eq(List.of(new StoredRecommendation("2007", 0.66, List.of()))), anyLong());
        ArgumentCaptor<List<ChatMessage>> prompt = ArgumentCaptor.forClass(List.class);
        verify(llm).chatStream(prompt.capture());
        assertThat(prompt.getValue().get(prompt.getValue().size() - 1).content())
                .contains("《Java 并发编程实战 · 锁与并发容器》");
        assertThat(slots.tryAcquire(1003L)).as("结束后释放占位").isTrue();
    }

    @Test
    void invalidQuestionIsRejectedBeforeTakingTheSlot() {
        assertThatThrownBy(() -> service.ask(STUDENT, new AskRequest(null, "   ", null)))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(400));
        assertThatThrownBy(() -> service.ask(STUDENT, new AskRequest(null, "问".repeat(501), null)))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(400));
        verify(store, never()).create(anyLong(), anyString(), any());
        assertThat(slots.tryAcquire(1003L)).isTrue();
    }

    @Test
    void othersConversationIs404BeforeTakingTheSlot() {
        when(store.requireOwned(5L, 1003L)).thenThrow(new ServiceException(404, "会话不存在或已删除"));

        assertThatThrownBy(() -> service.ask(STUDENT, new AskRequest(5L, QUESTION, null)))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(404));
        assertThat(slots.tryAcquire(1003L)).isTrue();
    }

    @Test
    void secondQuestionWhileGeneratingIs409AndWritesNothing() {
        slots.tryAcquire(1003L);

        assertThatThrownBy(() -> service.ask(STUDENT, new AskRequest(null, "线程池参数怎么设", null)))
                .isInstanceOf(ServiceException.class)
                .hasMessage("上一个回答还在生成")
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(409));
        // 被拒绝的请求不能在「最近」里留下空会话，也不能在历史里留下没有回答的问题
        verify(store, never()).create(anyLong(), anyString(), any());
        verify(store, never()).appendUserMessage(any(), anyString());
    }

    @Test
    void failureBeforeStreamingReleasesTheSlot() {
        when(store.appendUserMessage(any(), anyString())).thenThrow(new IllegalStateException("数据库连接断开"));

        assertThatThrownBy(() -> service.ask(STUDENT, new AskRequest(null, QUESTION, null)))
                .isInstanceOf(ServiceException.class)
                .hasMessage("发送失败，请重试")
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(500));
        assertThat(slots.tryAcquire(1003L)).as("否则这个学生之后每次提问都是 409，直到服务重启").isTrue();
    }

    @Test
    void modelFailureAfterSomeTextEndsWithErrorEventAndKeepsThePartialAnswer() throws Exception {
        when(llm.chatStream(anyList())).thenReturn(Flux.concat(Flux.just("Hash"),
                Flux.error(new ServiceException(503, "AI 服务暂不可用，请稍后再试"))));

        List<ServerSentEvent<String>> events = service.ask(STUDENT, new AskRequest(null, QUESTION, null))
                .collectList().block(Duration.ofSeconds(5));

        assertThat(events).extracting(ServerSentEvent::event).containsExactly("meta", "delta", "error");
        JsonNode error = json.readTree(events.get(2).data());
        assertThat(error.path("code").asInt()).isEqualTo(503);
        assertThat(error.path("msg").asText()).isEqualTo("AI 服务暂不可用，请稍后再试");
        verify(store).saveAssistantMessage(anyLong(), eq(conversation), eq("Hash"), eq(MessageStatus.FAILED),
                anyList(), eq(List.of()), anyLong());
        assertThat(slots.tryAcquire(1003L)).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void retrievalFailureStillAnswersWithANoticeAndNoRecommendation() throws Exception {
        when(embedding.embed(anyString())).thenThrow(new ServiceException(503, "AI 服务暂不可用，请稍后再试"));
        when(llm.chatStream(anyList())).thenReturn(Flux.just("答案"));

        List<ServerSentEvent<String>> events = service.ask(STUDENT, new AskRequest(null, QUESTION, null))
                .collectList().block(Duration.ofSeconds(5));

        JsonNode done = json.readTree(events.get(events.size() - 1).data());
        assertThat(done.path("notice").asText()).isEqualTo(ConversationChatService.RETRIEVAL_NOTICE);
        assertThat(done.path("recommendations").size()).isZero();
        verify(recommender, never()).recommend(anyList(), anyList(), any());
        ArgumentCaptor<List<ChatMessage>> prompt = ArgumentCaptor.forClass(List.class);
        verify(llm).chatStream(prompt.capture());
        assertThat(prompt.getValue().get(prompt.getValue().size() - 1).content()).contains("（未检索到相关资料）");
    }

    @Test
    void followUpUsesConversationScopeAndPreviousQuestion() {
        conversation.setCourseId(null);
        when(store.requireOwned(100L, 1003L)).thenReturn(conversation);
        when(store.previousQuestion(100L, 101L)).thenReturn(QUESTION);
        when(llm.chatStream(anyList())).thenReturn(Flux.just("用 CAS 加桶级锁"));

        service.ask(STUDENT, new AskRequest(100L, "那 ConcurrentHashMap 呢", 2007L)).blockLast(Duration.ofSeconds(5));

        verify(store, never()).create(anyLong(), anyString(), any());
        verify(embedding).embed("那 ConcurrentHashMap 呢\n" + QUESTION);
        // 已有会话的范围以会话为准，请求里的 courseId=2007 被忽略
        verify(knowledge).retrieveScored(anyString(), anyList(), eq(8), isNull());
    }

    @Test
    void cancellingSavesWhatWasGeneratedAsStoppedAndReleasesTheSlot() throws Exception {
        when(llm.chatStream(anyList())).thenReturn(Flux.interval(Duration.ofMillis(50)).map(i -> "片" + i));
        CountDownLatch firstDelta = new CountDownLatch(1);

        Disposable subscription = service.ask(STUDENT, new AskRequest(null, "讲讲线程池", null))
                .subscribe(event -> {
                    if ("delta".equals(event.event())) {
                        firstDelta.countDown();
                    }
                });
        assertThat(firstDelta.await(5, TimeUnit.SECONDS)).isTrue();
        subscription.dispose();

        verify(store, timeout(3000)).saveAssistantMessage(anyLong(), eq(conversation), startsWith("片"),
                eq(MessageStatus.STOPPED), anyList(), eq(List.of()), anyLong());
        verify(store, never()).saveAssistantMessage(anyLong(), any(), anyString(), eq(MessageStatus.COMPLETED),
                anyList(), anyList(), anyLong());
        assertThat(slots.tryAcquire(1003L)).isTrue();
    }
}
