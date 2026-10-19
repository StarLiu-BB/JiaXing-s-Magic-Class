package com.zhixue.ai.controller;

import com.zhixue.ai.service.LlmService;
import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.security.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 内容审核：仅供内部服务（弹幕持久化）调用。
 *
 * <p>修复前经网关登录即可调用，等于把本机模型当成任何人可用的免费接口；
 * 且调用失败时返回的错误文本会被调用方当成"违规原因"。</p>
 */
@Slf4j
@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditController {

    static final int MAX_CONTENT_LENGTH = 500;

    private final LlmService llmService;

    /**
     * @return 安全时 data 为 "OK"，否则为风险说明；模型不可用时返回 503，由调用方决定放行或拦截
     */
    @PostMapping("/text")
    public Mono<R<String>> auditText(@RequestBody Map<String, String> body) {
        if (!SecurityUtils.isTrustedInternalCall()) {
            return Mono.just(R.fail(HttpStatus.FORBIDDEN, "仅限内部服务调用"));
        }
        String content = body == null ? null : body.get("content");
        if (content == null || content.isBlank()) {
            return Mono.just(R.fail(HttpStatus.BAD_REQUEST, "内容不能为空"));
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            return Mono.just(R.fail(HttpStatus.BAD_REQUEST, "内容过长"));
        }
        return llmService.audit(content)
                .map(R::ok)
                .onErrorResume(ServiceException.class, e -> Mono.just(R.fail(e.getCode(), e.getMessage())));
    }
}
