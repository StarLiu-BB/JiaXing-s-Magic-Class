package com.zhixue.ai.controller;

import com.zhixue.common.core.domain.R;
import com.zhixue.ai.service.KnowledgeBaseService;
import com.zhixue.ai.service.LlmService;
import com.zhixue.ai.service.PromptTemplateService;
import com.zhixue.ai.domain.entity.PromptTemplate;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 对话控制器。
 * 这个类提供智能问答接口，学生可以问课程相关的问题，AI 会根据知识库内容给出回答。
 * 它会先从知识库里找相关的课程内容，然后让 AI 基于这些内容来回答问题。
 */
@Slf4j
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class AiChatController {

    private final LlmService llmService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final PromptTemplateService promptTemplateService;

    /**
     * 课程答疑接口。
     * 这个接口会先从知识库里找和问题相关的课程内容，然后用这些内容去问 AI，让 AI 基于知识库来回答。
     * @param question 学生提出的问题，不能为空
     * @param courseId 课程编号，可选参数，用于限定搜索范围
     * @return AI 生成的回答
     */
    @GetMapping("/course")
    public Mono<R<String>> courseQa(@RequestParam @NotBlank String question,
                                    @RequestParam(required = false) Long courseId) {
        log.info("收到课程答疑请求: question={}, courseId={}", question, courseId);
        try {
            // 从知识库中查找相关内容，最多找 5 条
            List<String> context = knowledgeBaseService.retrieveContext(question, 5);
            log.info("检索到相关内容 {} 条", context.size());
            
            String contextText = context.stream()
                    .limit(5)
                    .collect(Collectors.joining("\n\n"));

            // 获取问答模板，如果没有就用默认模板
            PromptTemplate template = promptTemplateService.getByCode("COURSE_QA");
            String tpl = template != null ? template.getContent() :
                    "你是在线课程答疑助手，请根据给定的课程知识回答学生问题，回答使用中文。\n" +
                            "【课程知识】:\n{{context}}\n\n【问题】:\n{{question}}\n\n请给出清晰分点回答。";

            // 把知识库内容和问题填入模板，生成最终的提示词
            String prompt = tpl.replace("{{context}}", contextText)
                    .replace("{{question}}", question);
            
            log.info("生成的 Prompt 长度: {}", prompt.length());

            // 调用 AI 模型生成回答
            return llmService.chat(prompt)
                    .map(R::ok)
                    .doOnError(e -> log.error("LLM 调用发生错误", e));
        } catch (Exception e) {
            log.error("处理课程答疑请求失败", e);
            String errorMsg = e.getMessage();
            if (errorMsg == null) {
                errorMsg = e.getClass().getName();
            }
            return Mono.just(R.fail("系统繁忙: " + errorMsg));
        }
    }
}


