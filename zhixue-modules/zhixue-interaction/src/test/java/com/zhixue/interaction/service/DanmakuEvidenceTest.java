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
    @Test void unknownAuditStatusCannotBePersistedAsApproved() {
        var dto = new DanmakuMessageDTO(); dto.setAuditStatus(9);
        assertThatThrownBy(() -> persistence.persist(dto)).hasMessageContaining("审核状态");
        verifyNoInteractions(mapper, audit);
    }
}
