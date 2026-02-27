package com.zhixue.ai.audit;

import com.zhixue.ai.service.LlmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 语义审核器。
 * 这个类用 AI 模型检查内容的语义是否合规。
 * 优点是可以识别隐含的违规内容，缺点是速度较慢。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SemanticAuditor {

    private final LlmService llmService;

    /**
     * 检查内容的语义是否合规。
     * 把内容发给 AI 模型，让 AI 判断是否有风险。
     * @param content 需要检查的文本内容
     * @return 如果内容合规返回 true，不合规返回 false
     */
    public boolean isAllowed(String content) {
        try {
            // 调用 AI 模型审核内容
            String result = llmService.audit(content).block();
            if (result == null) {
                return true;
            }
            String trimmed = result.trim();
            // 约定：AI 返回 "OK" 视为通过，其他内容包含风险说明
            return "OK".equalsIgnoreCase(trimmed);
        } catch (Exception e) {
            log.error("语义审核失败，默认放行 content={}", content, e);
            // 审核失败时默认放行，避免影响正常使用
            return true;
        }
    }
}


