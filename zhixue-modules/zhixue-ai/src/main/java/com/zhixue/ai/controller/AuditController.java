package com.zhixue.ai.controller;

import com.zhixue.ai.service.LlmService;
import com.zhixue.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 内容审核控制器。
 * 提供 AI 内容审核接口，用于检查文本是否违规。
 */
@Slf4j
@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditController {

    private final LlmService llmService;

    /**
     * 审核文本内容。
     * @param body 请求体，包含 content 字段
     * @return 审核结果，如果安全返回 OK，否则返回风险说明
     */
    @PostMapping("/text")
    public Mono<R<String>> auditText(@RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (content == null || content.isBlank()) {
            return Mono.just(R.fail("内容不能为空"));
        }
        return llmService.audit(content)
                .map(R::ok)
                .doOnError(e -> log.error("审核失败", e))
                .onErrorResume(e -> Mono.just(R.fail("审核服务暂不可用")));
    }
}
