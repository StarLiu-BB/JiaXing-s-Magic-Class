package com.zhixue.interaction.service;

import com.zhixue.api.ai.RemoteAiService;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.security.config.SecurityProperties;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DanmakuAuditServiceTest {
    private final RemoteAiService remote = mock(RemoteAiService.class);
    private final DanmakuAuditService service = new DanmakuAuditService(remote, new SecurityProperties());

    @Test void explicitOkIsApprovedAndRejectionReasonIsRetained() {
        when(remote.auditText(anyMap(), anyString(), nullable(String.class))).thenReturn(R.ok(" OK "));
        assertThat(service.audit("内容").status()).isEqualTo(1);
        when(remote.auditText(anyMap(), anyString(), nullable(String.class))).thenReturn(R.ok("不符合规则"));
        assertThat(service.audit("内容").status()).isEqualTo(2);
        assertThat(service.audit("内容").remark()).isEqualTo("不符合规则");
    }

    @Test void emptyInvalidOrFailedAuditIsPendingRatherThanApproved() {
        for (R<String> response : java.util.Arrays.<R<String>>asList(null, R.ok(null), R.ok("  "), R.fail(503, "busy"))) {
            when(remote.auditText(anyMap(), anyString(), nullable(String.class))).thenReturn(response);
            assertThat(service.audit("内容").status()).isZero();
        }
        when(remote.auditText(anyMap(), anyString(), nullable(String.class))).thenThrow(new RuntimeException("private-url"));
        assertThat(service.audit("内容").status()).isZero();
    }

    @Test void modelRemarkIsBoundedToDatabaseColumn() {
        when(remote.auditText(anyMap(), anyString(), nullable(String.class))).thenReturn(R.ok("x".repeat(800)));
        assertThat(service.audit("内容").remark()).hasSize(255);
    }
}
