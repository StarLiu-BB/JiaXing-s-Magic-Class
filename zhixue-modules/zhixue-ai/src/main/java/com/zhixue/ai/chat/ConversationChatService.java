package com.zhixue.ai.chat;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.zhixue.ai.domain.entity.AiConversation;
import com.zhixue.ai.domain.entity.AiMessage;
import com.zhixue.ai.service.EmbeddingService;
import com.zhixue.ai.service.KnowledgeBaseService;
import com.zhixue.ai.service.KnowledgeBaseService.RetrievedChunk;
import com.zhixue.ai.service.LlmService;
import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 一次提问的编排：校验 → 占位 → 建会话/存问题 → meta → 检索 → 流式 delta → 推荐 → 落库 → done。
 *
 * <p>同步部分在请求线程上完成，失败直接抛 ServiceException，由全局异常处理器按普通 JSON 返回（流前错误）。
 * 流开始后不再抛异常：失败以 error 事件结束；学生点停止或断线则保存为「已停止」。
 * 三种结束方式都只落库一次、只释放一次占位。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationChatService {

    static final int MAX_QUESTION_LENGTH = 500;
    static final int RETRIEVE_TOP_K = 8;
    static final String RETRIEVAL_NOTICE = "课程资料暂时无法检索，本次回答未参考课程资料";

    private final ConversationStore store;
    private final GenerationSlots slots;
    private final PromptAssembler assembler;
    private final EmbeddingService embeddingService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final LlmService llmService;
    private final CourseRecommender recommender;

    /** 同一次检索的结果：问句向量（供课时匹配复用）、命中、降级提示 */
    private record Retrieval(List<Float> vector, List<RetrievedChunk> hits, String notice) {
    }

    /** 一次生成的状态；回答缓冲由模型返回线程追加、由取消线程读取，因此用 StringBuffer */
    private static final class Generation {
        private final AiConversation conversation;
        private final long assistantId;
        private final String question;
        private final List<ConversationStore.Turn> history;
        private final String previousQuestion;
        private final long startNanos = System.nanoTime();
        private final StringBuffer answer = new StringBuffer();
        private final AtomicBoolean finished = new AtomicBoolean();

        private Generation(AiConversation conversation, long assistantId, String question,
                           List<ConversationStore.Turn> history, String previousQuestion) {
            this.conversation = conversation;
            this.assistantId = assistantId;
            this.question = question;
            this.history = history;
            this.previousQuestion = previousQuestion;
        }

        private long costMs() {
            return (System.nanoTime() - startNanos) / 1_000_000;
        }
    }

    /**
     * @param viewer 必须在请求线程上取得
     * @throws ServiceException 流前错误：400 问题为空/过长、404 会话不存在、409 已有回答在生成、500 保存提问失败
     */
    public Flux<ServerSentEvent<String>> ask(Viewer viewer, AskRequest request) {
        String question = request == null || request.question() == null ? "" : request.question().strip();
        if (question.isEmpty()) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "问题不能为空");
        }
        if (question.length() > MAX_QUESTION_LENGTH) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "问题不能超过 " + MAX_QUESTION_LENGTH + " 字");
        }
        AiConversation existing = request.conversationId() == null ? null
                : store.requireOwned(request.conversationId(), viewer.userId());
        // 写任何数据之前原子占位：被 409 拒绝的请求不能留下空会话或没有回答的问题
        if (!slots.tryAcquire(viewer.userId())) {
            throw new ServiceException(HttpStatus.CONFLICT, "上一个回答还在生成");
        }
        Generation generation;
        try {
            generation = prepare(viewer, existing, request.courseId(), question);
        } catch (RuntimeException e) {
            // 占位后、流开始前的任何异常都必须释放，否则这个学生之后每次提问都是 409
            slots.release(viewer.userId());
            if (e instanceof ServiceException se) {
                throw se;
            }
            log.error("保存提问失败 userId={}", viewer.userId(), e);
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "发送失败，请重试", e);
        }
        AtomicBoolean released = new AtomicBoolean();
        return stream(viewer, generation)
                // 学生点停止或断线：取消订阅即关闭到 Ollama 的连接；已生成部分保存为「已停止」
                .doOnCancel(() -> Schedulers.boundedElastic().schedule(() -> stop(generation)))
                .doFinally(signal -> {
                    if (released.compareAndSet(false, true)) {
                        slots.release(viewer.userId());
                    }
                });
    }

    private Generation prepare(Viewer viewer, AiConversation existing, Long courseId, String question) {
        AiConversation conversation = existing != null ? existing : store.create(viewer.userId(), question, courseId);
        AiMessage userMessage = store.appendUserMessage(conversation, question);
        List<ConversationStore.Turn> history = store.completedHistory(
                conversation.getId(), userMessage.getId(), PromptAssembler.HISTORY_ROUNDS);
        String previous = store.previousQuestion(conversation.getId(), userMessage.getId());
        // 回答的 ID 先分配好：meta 事件就要告诉前端；雪花 ID 递增，保证排在这条问题之后
        return new Generation(conversation, IdWorker.getId(), question, history, previous);
    }

    private Flux<ServerSentEvent<String>> stream(Viewer viewer, Generation g) {
        Flux<ServerSentEvent<String>> answer = Mono.fromCallable(() -> retrieve(g))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(r -> llmService.chatStream(assembler.build(g.history, assembler.contextOf(r.hits()), g.question))
                        .map(text -> {
                            g.answer.append(text);
                            return ChatEvents.delta(text);
                        })
                        // 推荐要调 Feign、算向量，落库是阻塞的 JDBC：都放到 boundedElastic，不占模型返回线程
                        .concatWith(Mono.fromCallable(() -> complete(viewer, g, r))
                                .subscribeOn(Schedulers.boundedElastic())))
                .onErrorResume(e -> Mono.fromCallable(() -> fail(g, e)).subscribeOn(Schedulers.boundedElastic()));
        return Flux.just(ChatEvents.meta(g.conversation, g.assistantId)).concatWith(answer);
    }

    /** 检索失败（ES 或向量模型不可用）不中断回答：不带资料回答、不出推荐，并在 done 里说明 */
    private Retrieval retrieve(Generation g) {
        String query = PromptAssembler.retrievalQuery(g.question, g.previousQuestion);
        try {
            List<Float> vector = embeddingService.embed(query);
            List<RetrievedChunk> hits = knowledgeBaseService.retrieveScored(
                    query, vector, RETRIEVE_TOP_K, g.conversation.getCourseId());
            return new Retrieval(vector, hits, null);
        } catch (RuntimeException e) {
            log.warn("课程资料检索失败，本次不参考资料 conversationId={}：{}", g.conversation.getId(), e.getMessage());
            return new Retrieval(List.of(), List.of(), RETRIEVAL_NOTICE);
        }
    }

    /** 正常结束；若已被取消（stop 抢先落库）返回 null，Mono.fromCallable 视为空 */
    private ServerSentEvent<String> complete(Viewer viewer, Generation g, Retrieval r) {
        if (!g.finished.compareAndSet(false, true)) {
            return null;
        }
        List<Source> sources = assembler.contextOf(r.hits()).stream()
                .map(RetrievedChunk::title).distinct().map(Source::new).toList();
        CourseRecommender.Result recommendation = r.hits().isEmpty()
                ? CourseRecommender.Result.empty()
                : recommender.recommend(r.hits(), r.vector(), viewer);
        try {
            store.saveAssistantMessage(g.assistantId, g.conversation, g.answer.toString(), MessageStatus.COMPLETED,
                    sources, recommendation.stored(), g.costMs());
        } catch (RuntimeException e) {
            // 学生已经看到完整回答，不能因落库失败改成报错；历史里这一问会显示「未完成回答」
            log.error("保存回答失败 conversationId={} messageId={}", g.conversation.getId(), g.assistantId, e);
        }
        return ChatEvents.done(g.assistantId, sources, r.notice(), recommendation.cards());
    }

    private ServerSentEvent<String> fail(Generation g, Throwable e) {
        int code = e instanceof ServiceException se ? se.getCode() : HttpStatus.SERVICE_UNAVAILABLE;
        String msg = e instanceof ServiceException ? e.getMessage() : "AI 服务暂不可用，请稍后再试";
        if (!(e instanceof ServiceException)) {
            log.error("生成回答失败 conversationId={}", g.conversation.getId(), e);
        }
        if (g.finished.compareAndSet(false, true)) {
            saveQuietly(g, MessageStatus.FAILED);
        }
        return ChatEvents.failure(code, msg);
    }

    private void stop(Generation g) {
        if (g.finished.compareAndSet(false, true)) {
            saveQuietly(g, MessageStatus.STOPPED);
        }
    }

    private void saveQuietly(Generation g, int status) {
        try {
            store.saveAssistantMessage(g.assistantId, g.conversation, g.answer.toString(), status,
                    List.of(), List.of(), g.costMs());
        } catch (RuntimeException e) {
            log.error("保存回答失败 messageId={} status={}", g.assistantId, status, e);
        }
    }
}
