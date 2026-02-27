package com.zhixue.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.ai.config.LlmConfig;
import com.zhixue.ai.service.LlmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * 大语言模型服务的实现类。
 * 这个类实现了和通义千问模型的对接，提供对话和内容审核功能。
 * 通过调用阿里云的接口来使用 AI 模型。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmServiceImpl implements LlmService {

    private final LlmConfig config;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 创建网络请求客户端。
     * 用来调用通义千问的接口。
     * @return 网络请求客户端
     */
    private WebClient client() {
        return WebClient.builder()
                .baseUrl("https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + config.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 和 AI 进行对话。
     * 把提示词发给通义千问模型，获取 AI 生成的回答。
     * @param prompt 给 AI 的提示词
     * @return AI 生成的回答
     */
    @Override
    public Mono<String> chat(String prompt) {
        // 构造请求参数，包含模型名称和用户消息
        Map<String, Object> body = Map.of(
                "model", config.getModel(),
                "input", Map.of("messages", new Object[]{
                        Map.of("role", "user", "content", prompt)
                })
        );
        return postAndExtractText(body);
    }

    /**
     * 审核文本内容。
     * 把内容发给 AI 模型，让 AI 判断是否有风险。
     * @param content 需要审核的文本内容
     * @return 如果有风险返回风险说明，没有风险返回空字符串
     */
    @Override
    public Mono<String> audit(String content) {
        // 构造审核提示词，让 AI 判断内容是否安全
        String prompt = "你是内容审核助手，请判断以下弹幕是否包含违规、辱骂、色情、违法信息。" +
                "如果安全，返回 OK；如果不安全，用简短中文说明风险点。\n内容：" + content;
        return chat(prompt);
    }

    /**
     * 发送请求并提取返回结果中的文本内容。
     * @param body 请求参数
     * @return AI 生成的文本内容
     */
    private Mono<String> postAndExtractText(Map<String, Object> body) {
        return client().post()
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10))
                .map(this::extractContentSafely)
                .onErrorResume(e -> {
                    log.error("调用 dashscope 失败", e);
                    return Mono.just("AI 服务暂不可用: " + e.getMessage());
                });
    }

    /**
     * 从返回结果中提取文本内容。
     * 处理不同格式的返回结果，提取出 AI 生成的文本。
     * @param json 接口返回的 JSON 字符串
     * @return 提取出的文本内容
     */
    private String extractContentSafely(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode output = root.path("output");
            if (output.has("text")) {
                return output.get("text").asText();
            }
            if (output.has("choices")) {
                JsonNode first = output.path("choices").get(0);
                if (first != null && first.has("message")) {
                    return first.path("message").path("content").asText("");
                }
            }
            return "未提取到内容，原始响应: " + json;
        } catch (Exception e) {
            log.error("解析 dashscope 返回结果失败: {}", json, e);
            return "解析异常: " + e.getMessage() + ", 原始响应: " + json;
        }
    }
}


