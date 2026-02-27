package com.zhixue.ai.service;

import reactor.core.publisher.Mono;

/**
 * 大语言模型服务接口。
 * 这个接口定义了和 AI 模型交互的方法，比如对话、内容审核等。
 * 目前对接的是通义千问模型。
 */
public interface LlmService {

    /**
     * 和 AI 进行对话。
     * 把提示词发给 AI 模型，AI 会根据提示词生成回答。
     * @param prompt 给 AI 的提示词，包含问题和相关背景信息
     * @return AI 生成的回答
     */
    Mono<String> chat(String prompt);

    /**
     * 审核文本内容。
     * 检查内容是否有风险，比如敏感词、违规内容等。
     * @param content 需要审核的文本内容
     * @return 如果有风险返回风险说明，没有风险返回空字符串
     */
    Mono<String> audit(String content);
}


