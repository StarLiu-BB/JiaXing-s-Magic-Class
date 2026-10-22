package com.zhixue.interaction.service;

import com.zhixue.interaction.domain.dto.DanmakuMessageDTO;
import com.zhixue.interaction.domain.entity.Danmaku;
import com.zhixue.interaction.mapper.DanmakuMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DanmakuEvidenceTest {
    private final DanmakuMapper mapper = mock(DanmakuMapper.class);
    private final DanmakuAuditService audit = mock(DanmakuAuditService.class);
    private final DanmakuPersistenceService persistence = new DanmakuPersistenceService(mapper, audit);

    @Test void newMessageStoresServerDecisionWithoutAuditingAgain() {
        var dto = new DanmakuMessageDTO(); dto.setContent("内容"); dto.setAuditStatus(2); dto.setAuditRemark("原因");
        when(mapper.insert(any(Danmaku.class))).thenReturn(1);
        persistence.persist(dto);
        var captured = org.mockito.ArgumentCaptor.forClass(Danmaku.class);
        verify(mapper).insert(captured.capture());
        assertThat(captured.getValue().getAuditStatus()).isEqualTo(2);
        assertThat(captured.getValue().getAuditRemark()).isEqualTo("原因");
        verifyNoInteractions(audit);
    }
    @Test void oldMessageUsesSharedAuditWithoutBroadcasting() {
        var dto = new DanmakuMessageDTO(); dto.setContent("内容");
        when(audit.audit("内容")).thenReturn(new DanmakuAuditService.Decision(0, "审核不可用"));
        when(mapper.insert(any(Danmaku.class))).thenReturn(1);
        persistence.persist(dto);
        var captured = org.mockito.ArgumentCaptor.forClass(Danmaku.class);
        verify(mapper).insert(captured.capture());
        assertThat(captured.getValue().getAuditStatus()).isZero();
    }
    @Test void failedInsertCannotPretendEvidenceWasRetained() {
        var dto = new DanmakuMessageDTO(); dto.setAuditStatus(1);
        assertThatThrownBy(() -> persistence.persist(dto)).hasMessageContaining("保存失败");
    }
    @Test void replayAfterManualReviewDoesNotOverwriteDecision() {
        var dto = new DanmakuMessageDTO(); dto.setMessageId("message-1"); dto.setContent("内容"); dto.setRoomId(2007L); dto.setUserId(1003L); dto.setAuditStatus(2);
        var saved = new Danmaku(); saved.setMessageId("message-1"); saved.setContent("内容"); saved.setRoomId(2007L); saved.setUserId(1003L); saved.setAuditStatus(1);
        when(mapper.findMessage("message-1")).thenReturn(saved);
        persistence.persist(dto);
        verify(mapper, never()).insert(any(Danmaku.class)); verifyNoInteractions(audit);
    }

    @Test void idCollisionCannotSilentlyDropDifferentEvidence() {
        var dto = new DanmakuMessageDTO(); dto.setMessageId("message-1"); dto.setContent("new"); dto.setAuditStatus(2);
        var saved = new Danmaku(); saved.setContent("different");
        when(mapper.findMessage("message-1")).thenReturn(saved);
        assertThatThrownBy(() -> persistence.persist(dto)).hasMessageContaining("消息ID冲突");
        verify(mapper, never()).insert(any(Danmaku.class));
    }

    @Test void concurrentDuplicateStillChecksMessageFingerprint() {
        var dto = new DanmakuMessageDTO(); dto.setMessageId("message-1"); dto.setContent("new"); dto.setAuditStatus(2);
        var saved = new Danmaku(); saved.setContent("different");
        when(mapper.findMessage("message-1")).thenReturn(null, saved);
        when(mapper.insert(any(Danmaku.class))).thenThrow(new org.springframework.dao.DuplicateKeyException("unique"));
        assertThatThrownBy(() -> persistence.persist(dto)).hasMessageContaining("消息ID冲突");
    }

    @Test void identicalLegacyMessageWithOmittedTimePointCanBeReplayed() {
        var dto = new DanmakuMessageDTO(); dto.setMessageId("legacy-1"); dto.setContent("内容"); dto.setAuditStatus(2);
        var saved = new Danmaku(); saved.setContent("内容"); saved.setTimePoint(0);
        when(mapper.findMessage("legacy-1")).thenReturn(saved);
        org.assertj.core.api.Assertions.assertThatCode(() -> persistence.persist(dto)).doesNotThrowAnyException();
        verify(mapper, never()).insert(any(Danmaku.class));
    }

    @Test void unknownAuditStatusCannotBePersistedAsApproved() {
        var dto = new DanmakuMessageDTO(); dto.setAuditStatus(9);
        assertThatThrownBy(() -> persistence.persist(dto)).hasMessageContaining("审核状态");
        verifyNoInteractions(mapper, audit);
    }
}
