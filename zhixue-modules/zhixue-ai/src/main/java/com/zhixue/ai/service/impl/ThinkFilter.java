package com.zhixue.ai.service.impl;

/**
 * 跨块过滤模型输出中的 &lt;think&gt;…&lt;/think&gt;。
 *
 * <p>流式输出时标签可能被拆在两个块里（如 "&lt;thi" + "nk&gt;"）：块末尾若是标签的不完整前缀，
 * 先暂存，与下一块拼起来再判断；处于推理块内时一律不下发。每次流式调用使用一个新实例。</p>
 */
final class ThinkFilter {

    private static final String OPEN = "<think>";
    private static final String CLOSE = "</think>";

    private final StringBuilder pending = new StringBuilder();
    private boolean inside;

    /** 输入一个片段，返回可以下发的文本（可能为空串） */
    String accept(String chunk) {
        if (chunk != null) {
            pending.append(chunk);
        }
        return drain(false);
    }

    /** 流结束：暂存的普通文本照常下发；未闭合的推理块丢弃 */
    String finish() {
        return drain(true);
    }

    private String drain(boolean end) {
        StringBuilder out = new StringBuilder();
        while (true) {
            String tag = inside ? CLOSE : OPEN;
            int at = pending.indexOf(tag);
            if (at >= 0) {
                if (!inside) {
                    out.append(pending, 0, at);
                }
                pending.delete(0, at + tag.length());
                inside = !inside;
                continue;
            }
            int keep = end ? 0 : partialSuffix(pending, tag);
            if (!inside) {
                out.append(pending, 0, pending.length() - keep);
            }
            pending.delete(0, pending.length() - keep);
            return out.toString();
        }
    }

    /** text 末尾有多少个字符可能是 tag 的开头（如 "&lt;thi"） */
    private static int partialSuffix(CharSequence text, String tag) {
        for (int len = Math.min(tag.length() - 1, text.length()); len > 0; len--) {
            if (tag.startsWith(text.subSequence(text.length() - len, text.length()).toString())) {
                return len;
            }
        }
        return 0;
    }
}
