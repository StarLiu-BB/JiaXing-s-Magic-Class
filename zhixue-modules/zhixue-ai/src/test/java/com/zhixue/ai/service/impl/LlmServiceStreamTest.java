package com.zhixue.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhixue.ai.config.LlmConfig;
import com.zhixue.ai.service.LlmService.ChatMessage;
import com.zhixue.ai.support.FakeOllama;
import com.zhixue.common.core.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 真流式：边生成边下发；首块超时与总时长超时分开提示；失败不泄露内部地址 */
class LlmServiceStreamTest {

    private FakeOllama ollama;

    @BeforeEach
    void start() throws Exception {
        ollama = new FakeOllama();
    }

    @AfterEach
    void stop() {
        ollama.close();
    }

    private LlmServiceImpl service(int firstChunkSeconds, int totalSeconds) {
        LlmConfig config = new LlmConfig();
        config.setBaseUrl(ollama.baseUrl());
        config.setChatModel("qwen3.5:9b");
        config.setFirstChunkTimeoutSeconds(firstChunkSeconds);
        config.setChatTimeoutSeconds(totalSeconds);
        LlmServiceImpl service = new LlmServiceImpl(config);
        ReflectionTestUtils.setField(service, "aiMode", "real");
        return service;
    }

    private static List<ChatMessage> conversation() {
        return List.of(
                new ChatMessage("system", "你是课程助教"),
                new ChatMessage("user", "HashMap 是什么"),
                new ChatMessage("assistant", "一种哈希表"),
                new ChatMessage("user", "它为什么线程不安全"));
    }

    @Test
    void streamsPiecesInOrderAndSendsAStreamingRequest() {
        ollama.streamChat(0, 30, "HashMap ", "不是", "线程安全的");

        List<String> pieces = service(5, 10).chatStream(conversation()).collectList().block(Duration.ofSeconds(10));

        assertThat(pieces).hasSizeGreaterThan(1); // 逐块到达，而不是攒成一整段
        assertThat(String.join("", pieces)).isEqualTo("HashMap 不是线程安全的");
        JsonNode request = ollama.lastRequest();
        assertThat(request.path("stream").asBoolean()).isTrue();
        assertThat(request.path("think").asBoolean(true)).isFalse();
        assertThat(request.path("options").path("num_predict").asInt()).isEqualTo(1024);
        List<String> roles = new ArrayList<>();
        request.path("messages").forEach(m -> roles.add(m.path("role").asText()));
        assertThat(roles).containsExactly("system", "user", "assistant", "user");
    }

    @Test
    void removesThinkBlockSplitAcrossChunksAndLeadingBlankLines() {
        ollama.streamChat(0, 10, "<thi", "nk>先想一想", "</th", "ink>\n\n答案", "是 B");

        List<String> pieces = service(5, 10).chatStream(conversation()).collectList().block(Duration.ofSeconds(10));

        assertThat(String.join("", pieces)).isEqualTo("答案是 B");
    }

    @Test
    void noChunkWithinFirstChunkTimeoutIs504NotResponding() {
        ollama.streamChat(2500, 0, "迟到的回答");

        assertThatThrownBy(() -> service(1, 10).chatStream(conversation()).blockLast(Duration.ofSeconds(10)))
                .isInstanceOf(ServiceException.class)
                .hasMessage("AI 响应超时")
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(504));
    }

    @Test
    void exceedingTotalTimeoutKeepsWhatArrivedAndIs504Interrupted() {
        ollama.streamChat(0, 700, "一", "二", "三", "四", "五");
        List<String> received = new CopyOnWriteArrayList<>();

        assertThatThrownBy(() -> service(1, 2).chatStream(conversation())
                .doOnNext(received::add).blockLast(Duration.ofSeconds(10)))
                .isInstanceOf(ServiceException.class)
                .hasMessage("生成超时，已中断");
        assertThat(received).isNotEmpty();
    }

    @Test
    void ollamaErrorIs503WithoutLeakingAddress() {
        ollama.on("/api/chat", body -> new FakeOllama.Reply(500, "{\"error\":\"model not found\"}"));

        assertThatThrownBy(() -> service(5, 10).chatStream(conversation()).blockLast(Duration.ofSeconds(10)))
                .isInstanceOf(ServiceException.class)
                .hasMessage("AI 服务暂不可用，请稍后再试")
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(503));
    }

    @Test
    void onlyThinkingOrBlankIsAnEmptyAnswer() {
        ollama.streamChat(0, 0, "<think>只有推理</think>", "  \n");

        assertThatThrownBy(() -> service(5, 10).chatStream(conversation()).blockLast(Duration.ofSeconds(10)))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(503));
    }
}
