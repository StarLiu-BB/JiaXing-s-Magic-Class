package com.zhixue.ai.service.impl;

import com.zhixue.ai.audit.KeywordAuditor;
import com.zhixue.ai.audit.SemanticAuditor;
import com.zhixue.ai.service.ContentAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 内容审核服务的实现类。
 * 这个类实现了内容审核功能，使用关键字和语义两种方式检查内容。
 * 只有两种方式都通过，内容才算合规。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentAuditServiceImpl implements ContentAuditService {

    private final KeywordAuditor keywordAuditor;
    private final SemanticAuditor semanticAuditor;

    /**
     * 审核内容是否合规。
     * 先用关键字检查，再用语义检查，两种方式都通过才算合规。
     * @param content 需要审核的文本内容
     * @return 如果内容合规返回 true，不合规返回 false
     */
    @Override
    public boolean isAllowed(String content) {
        // 先检查是否包含敏感关键字
        if (!keywordAuditor.isAllowed(content)) {
            log.info("关键字审核未通过: {}", content);
            return false;
        }
        // 再用 AI 检查语义是否合规
        if (!semanticAuditor.isAllowed(content)) {
            log.info("语义审核未通过: {}", content);
            return false;
        }
        return true;
    }
}


