package com.zhixue.ai.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 流式输出时 &lt;think&gt; 可能被拆在两个块里：整段正则只能处理完整文本，
 * 学生会先看到一段推理内容，再看到它消失。过滤器必须跨块保持状态。
 */
class ThinkFilterTest {

    @Test
    void plainTextPassesThrough() {
        ThinkFilter filter = new ThinkFilter();
        assertThat(filter.accept("HashMap ")).isEqualTo("HashMap ");
        assertThat(filter.accept("不是线程安全的")).isEqualTo("不是线程安全的");
        assertThat(filter.finish()).isEmpty();
    }

    @Test
    void thinkBlockSplitAcrossChunksIsRemoved() {
        ThinkFilter filter = new ThinkFilter();
        assertThat(filter.accept("前言<thi")).isEqualTo("前言");
        assertThat(filter.accept("nk>先想一想</th")).isEmpty();
        assertThat(filter.accept("ink>答案")).isEqualTo("答案");
        assertThat(filter.finish()).isEmpty();
    }

    @Test
    void unclosedThinkBlockIsDroppedAtEnd() {
        ThinkFilter filter = new ThinkFilter();
        assertThat(filter.accept("结论<think>推理一直没结束")).isEqualTo("结论");
        assertThat(filter.finish()).isEmpty();
    }

    @Test
    void lookalikePrefixIsReleasedOnceItCannotBeATag() {
        ThinkFilter filter = new ThinkFilter();
        assertThat(filter.accept("a<th")).isEqualTo("a");
        assertThat(filter.accept("x>b")).isEqualTo("<thx>b");
        assertThat(filter.accept("比较 a<")).isEqualTo("比较 a");
        assertThat(filter.finish()).isEqualTo("<");
    }
}
