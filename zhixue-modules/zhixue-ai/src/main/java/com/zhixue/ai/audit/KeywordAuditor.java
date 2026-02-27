package com.zhixue.ai.audit;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 关键字审核器。
 * 这个类用关键字的方式快速检查内容是否包含敏感词。
 * 优点是速度快，缺点是只能检查已知的敏感词。
 */
@Component
public class KeywordAuditor {

    /**
     * 敏感词列表，包含常见的违规词汇
     */
    private final List<String> keywords = List.of(
            "傻逼", "垃圾", "色情", "赌博", "欺诈"
    );

    /**
     * 检查内容是否包含敏感词。
     * @param content 需要检查的文本内容
     * @return 如果不包含敏感词返回 true，包含返回 false
     */
    public boolean isAllowed(String content) {
        if (content == null || content.isEmpty()) {
            return true;
        }
        // 转成小写后再检查，避免大小写问题
        String lower = content.toLowerCase();
        // 检查内容中是否包含任何一个敏感词
        return keywords.stream().noneMatch(lower::contains);
    }
}


