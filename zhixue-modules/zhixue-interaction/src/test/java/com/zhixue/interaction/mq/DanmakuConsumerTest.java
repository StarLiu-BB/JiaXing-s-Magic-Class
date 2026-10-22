package com.zhixue.interaction.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.interaction.domain.entity.Danmaku;
import com.zhixue.interaction.mapper.DanmakuMapper;
import com.zhixue.interaction.service.DanmakuAuditService;
import com.zhixue.interaction.service.DanmakuPersistenceService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DanmakuConsumerTest {
    private final DanmakuMapper mapper = mock(DanmakuMapper.class);
    private final DanmakuAuditService audit = mock(DanmakuAuditService.class);
    private final DanmakuConsumer consumer = new DanmakuConsumer(new DanmakuPersistenceService(mapper, audit), new ObjectMapper());

    @Test void oldMqMessageIsAuditedAndPendingRatherThanSilentlyApproved() {
        when(audit.audit("旧消息")).thenReturn(new DanmakuAuditService.Decision(0, "审核不可用"));
        when(mapper.insert(any(Danmaku.class))).thenReturn(1);
        consumer.onMessage("{\"roomId\":2007,\"userId\":1003,\"content\":\"旧消息\",\"timePoint\":2}");
        var captured = org.mockito.ArgumentCaptor.forClass(Danmaku.class);
        verify(mapper).insert(captured.capture());
        assertThat(captured.getValue().getAuditStatus()).isZero();
        assertThat(captured.getValue().getAuditRemark()).isEqualTo("审核不可用");
    }

    @Test void newRejectedMessageKeepsDecisionWithoutSecondAiCall() {
        when(mapper.insert(any(Danmaku.class))).thenReturn(1);
        consumer.onMessage("{\"roomId\":2007,\"userId\":1003,\"content\":\"内容\",\"auditStatus\":2,\"auditRemark\":\"拒绝原因\"}");
        verifyNoInteractions(audit);
        var captured = org.mockito.ArgumentCaptor.forClass(Danmaku.class);
        verify(mapper).insert(captured.capture());
        assertThat(captured.getValue().getAuditStatus()).isEqualTo(2);
        assertThat(captured.getValue().getAuditRemark()).isEqualTo("拒绝原因");
    }

    @Test void storageFailurePropagatesSoRabbitCannotAcknowledgeLostEvidence() {
        assertThatThrownBy(() -> consumer.onMessage("{\"auditStatus\":1}"))
                .isInstanceOf(RuntimeException.class).hasMessageContaining("保存失败");
    }

    @Test void malformedMessageIsRejectedWithoutRequeueLoop() {
        assertThatThrownBy(() -> consumer.onMessage("invalid-json"))
                .isInstanceOf(org.springframework.amqp.AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(audit, mapper);
    }
}
