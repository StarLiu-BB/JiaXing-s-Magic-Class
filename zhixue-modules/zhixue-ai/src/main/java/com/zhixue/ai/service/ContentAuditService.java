package com.zhixue.ai.service;

/**
 * 内容审核服务接口。
 * 这个接口定义了内容审核的方法，检查内容是否合规。
 * 比如检查是否有敏感词、违规内容等。
 */
public interface ContentAuditService {

    /**
     * 审核内容是否合规。
     * 检查内容中是否有敏感词或违规内容。
     * @param content 需要审核的文本内容
     * @return 如果内容合规返回 true，不合规返回 false
     */
    boolean isAllowed(String content);
}


