package com.zhixue.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhixue.ai.config.LlmConfig;
import com.zhixue.ai.support.FakeOllama;
import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 本地 Ollama 对话与审核。
 *
 * <p>修复前：只对接阿里云 DashScope；调用失败时把 "AI 服务调用失败: ..."、"沙箱模式已降级..." 之类的
 * 错误文本当作正常回答返回，前端会把它显示成 AI 的答复；审核要求模型逐字返回 "OK"，
 * 小模型回 "OK。" 或带推理过程时，正常弹幕会被判为违规。</p>
 */
class LlmServiceImplTest {

    private FakeOllama ollama;

    @BeforeEach
    void setUp() throws Exception {
        ollama = new FakeOllama();
    }

    @AfterEach
    void tearDown() {
        ollama.close();
    }

    private LlmServiceImpl newService(String mode) {
        LlmConfig config = new LlmConfig();
        config.setBaseUrl(ollama.baseUrl());
        config.setChatModel("qwen3.5:9b");
        config.setChatTimeoutSeconds(5);
        config.setAuditTimeoutSeconds(5);
        LlmServiceImpl service = new LlmServiceImpl(config);
        ReflectionTestUtils.setField(service, "aiMode", mode);
        return service;
    }

    @Test
    void chatCallsLocalOllamaWithThinkingDisabledAndNoStreaming() {
        ollama.chatReplies("网关统一校验 JWT。");

        String answer = newService("real").chat("你是助教", "网关怎么鉴权？").block();

        assertThat(answer).isEqualTo("网关统一校验 JWT。");
        assertThat(ollama.paths()).containsExactly("/api/chat");
        JsonNode req = ollama.lastRequest();
        assertThat(req.path("model").asText()).isEqualTo("qwen3.5:9b");
        // Ollama 默认流式返回，且 qwen3.5 默认先输出推理过程：两者都必须显式关闭
        assertThat(req.path("stream").asBoolean(true)).isFalse();
        assertThat(req.path("think").asBoolean(true)).isFalse();
        assertThat(req.path("messages").get(0).path("role").asText()).isEqualTo("system");
        assertThat(req.path("messages").get(0).path("content").asText()).isEqualTo("你是助教");
        assertThat(req.path("messages").get(1).path("role").asText()).isEqualTo("user");
        assertThat(req.path("messages").get(1).path("content").asText()).isEqualTo("网关怎么鉴权？");
    }

    @Test
    void chatStripsLeakedReasoningBlock() {
        ollama.chatReplies("<think>先想一想</think>\n\n最终答案");
        assertThat(newService("real").chat("s", "q").block()).isEqualTo("最终答案");
    }

    @Test
    void chatFailureIsAnErrorNotAFakeAnswer() {
        ollama.on("/api/chat", body -> new FakeOllama.Reply(500, "{\"error\":\"model not found\"}"));

        assertThatThrownBy(() -> newService("real").chat("s", "q").block())
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void sandboxModeAlsoNeverFabricatesAnAnswer() {
        ollama.on("/api/chat", body -> new FakeOllama.Reply(500, "{}"));
        assertThatThrownBy(() -> newService("sandbox").chat("s", "q").block())
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void stubModeDoesNotTouchTheNetwork() {
        String result = newService("stub").chat("s", "如何学习 Spring Cloud？").block();
        assertThat(result).contains("stub");
        assertThat(ollama.paths()).isEmpty();
    }

    @Test
    void auditAsksForJsonWithoutOllamaStructuredOutputAtTemperatureZero() {
        ollama.chatReplies(FakeOllama.json(Map.of("safe", true, "reason", "")));

        assertThat(newService("real").audit("这门课程讲得很清楚").block()).isEqualTo("OK");

        JsonNode req = ollama.lastRequest();
        // 本机 qwen3.5:9b 为 nvfp4/MLX 量化版，Ollama 对它的 format（结构化输出）返回 501（实测）。
        // 只能靠提示词约束 JSON，再由服务端解析；带上 format 会让每一条审核都失败
        assertThat(req.has("format")).isFalse();
        assertThat(req.path("messages").get(0).path("content").asText()).contains("JSON").contains("safe");
        assertThat(req.path("options").path("temperature").asDouble(-1)).isEqualTo(0.0);
        assertThat(req.path("think").asBoolean(true)).isFalse();
        // 待审内容只能出现在 user 消息里，不能拼进系统指令
        assertThat(req.path("messages").get(0).path("content").asText()).doesNotContain("这门课程讲得很清楚");
        assertThat(req.path("messages").get(1).path("content").asText()).contains("这门课程讲得很清楚");
    }

    @Test
    void auditToleratesJsonWrappedInMarkdownOrProse() {
        ollama.chatReplies("```json\n{\"safe\": false, \"reason\": \"广告引流\"}\n```");
        assertThat(newService("real").audit("加微信领课").block()).isEqualTo("广告引流");
    }

    @Test
    void auditReturnsReasonWhenUnsafe() {
        ollama.chatReplies(FakeOllama.json(Map.of("safe", false, "reason", "人身攻击")));
        assertThat(newService("real").audit("老师你是个废物").block()).isEqualTo("人身攻击");
    }

    @Test
    void auditBlocksObviousRiskWordsWithoutCallingTheModel() {
        String result = newService("real").audit("你这个垃圾老师").block();
        assertThat(result).contains("风险词");
        assertThat(ollama.paths()).isEmpty();
    }

    @Test
    void auditModelFailureIsAnErrorSoTheCallerCanDecide() {
        // 修复前：失败文本被当作审核结论返回，调用方把正常弹幕标成"违规"
        ollama.on("/api/chat", body -> new FakeOllama.Reply(500, "{}"));
        assertThatThrownBy(() -> newService("real").audit("正常弹幕").block())
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void auditUnparseableModelOutputIsAnError() {
        ollama.chatReplies("OK。");
        assertThatThrownBy(() -> newService("real").audit("正常弹幕").block())
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void stubAuditUsesKeywordsOnly() {
        LlmServiceImpl service = newService("stub");
        assertThat(service.audit("这门课程讲得很清楚").block()).isEqualTo("OK");
        assertThat(service.audit("你这个垃圾老师").block()).contains("风险词");
        assertThat(ollama.paths()).isEmpty();
    }
}
