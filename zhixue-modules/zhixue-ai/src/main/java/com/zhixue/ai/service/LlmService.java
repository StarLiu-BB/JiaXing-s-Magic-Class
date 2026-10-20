package com.zhixue.ai.service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 大语言模型服务。
 */
public interface LlmService {

    /**
     * 对话。失败时以错误结束（{@code ServiceException}），绝不把错误文本伪装成回答。
     *
     * @param systemPrompt 系统指令
     * @param userPrompt   用户消息（含检索到的资料与问题）
     * @return 模型回答
     */
    Mono<String> chat(String systemPrompt, String userPrompt);

    /**
     * 审核文本。
     *
     * @param content 待审内容
     * @return 安全时返回 "OK"，否则返回简短的风险说明；模型不可用时以错误结束，由调用方决定放行或拦截
     */
    Mono<String> audit(String content);

    /** 一条对话消息；role 为 system / user / assistant */
    record ChatMessage(String role, String content) {
    }

    /**
     * 流式对话：逐块返回已过滤 &lt;think&gt;、已去掉开头空白的文本。
     *
     * <p>以 {@code ServiceException} 结束的情况：模型不可用或回答为空 503；
     * 首个返回块超时 504「AI 响应超时」；已开始返回但总时长超时 504「生成超时，已中断」。
     * 取消订阅会关闭到 Ollama 的连接，模型随即停止生成。</p>
     */
    Flux<String> chatStream(List<ChatMessage> messages);
}
