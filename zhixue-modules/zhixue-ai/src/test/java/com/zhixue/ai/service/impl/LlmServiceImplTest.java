package com.zhixue.ai.service.impl;

import com.zhixue.ai.config.LlmConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class LlmServiceImplTest {

    @Test
    void shouldReturnStubResponseWhenStubMode() {
        LlmConfig config = new LlmConfig();
        LlmServiceImpl service = new LlmServiceImpl(config);
        ReflectionTestUtils.setField(service, "aiMode", "stub");

        String result = service.chat("如何学习 Spring Cloud？").block();

        assertThat(result).contains("stub");
    }

    @Test
    void shouldUseLocalAuditWhenStubMode() {
        LlmConfig config = new LlmConfig();
        LlmServiceImpl service = new LlmServiceImpl(config);
        ReflectionTestUtils.setField(service, "aiMode", "stub");

        String safeResult = service.audit("这门课程讲得很清楚").block();
        String riskResult = service.audit("你这个垃圾老师").block();

        assertThat(safeResult).isEqualTo("OK");
        assertThat(riskResult).contains("风险词");
    }
}
