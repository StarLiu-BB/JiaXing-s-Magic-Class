package com.zhixue.ai.service;

import reactor.core.publisher.Mono;

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
}
