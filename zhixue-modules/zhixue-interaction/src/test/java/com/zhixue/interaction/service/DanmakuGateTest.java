package com.zhixue.interaction.service;

import com.zhixue.interaction.domain.dto.DanmakuMessageDTO;
import com.zhixue.interaction.mq.DanmakuProducer;
import com.zhixue.interaction.redis.RedisMessagePublisher;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DanmakuGateTest {
    private final DanmakuAuditService audit = mock(DanmakuAuditService.class);
    private final DanmakuProducer producer = mock(DanmakuProducer.class);
    private final RedisMessagePublisher publisher = mock(RedisMessagePublisher.class);

    private DanmakuMessageDTO message() {
        var dto = new DanmakuMessageDTO(); dto.setRoomId(2007L); dto.setUserId(1003L); dto.setContent("课程问题");
        return dto;
    }
    private DanmakuModerationService service(boolean failOpen) {
        return new DanmakuModerationService(audit, producer, publisher, Runnable::run, failOpen, 30000);
    }

    @Test void approvalIsRecordedBeforeBroadcast() {
        when(audit.audit("课程问题")).thenReturn(new DanmakuAuditService.Decision(1, null));
        var dto = message(); dto.setAuditStatus(2);
        assertThat(service(true).moderate(dto, () -> true).broadcast()).isTrue();
        var order = inOrder(audit, producer, publisher);
        order.verify(audit).audit("课程问题"); order.verify(producer).send(dto); order.verify(publisher).publish(dto);
        assertThat(dto.getAuditStatus()).isEqualTo(1);
    }
    @Test void rejectionRetainsEvidenceButNeverBroadcasts() {
        when(audit.audit("课程问题")).thenReturn(new DanmakuAuditService.Decision(2, "不符合规则"));
        var dto = message(); dto.setAuditStatus(1);
        assertThat(service(true).moderate(dto, () -> true).broadcast()).isFalse();
        verify(producer).send(dto); verifyNoInteractions(publisher);
        assertThat(dto.getAuditStatus()).isEqualTo(2);
        assertThat(dto.getAuditRemark()).isEqualTo("不符合规则");
    }
    @Test void unavailableAuditIsPendingAndFailOpenNotifiesSender() {
        when(audit.audit("课程问题")).thenReturn(new DanmakuAuditService.Decision(0, "审核不可用"));
        var dto = message(); var result = service(true).moderate(dto, () -> true);
        assertThat(result.broadcast()).isTrue(); assertThat(result.notice()).contains("待人工复核");
        assertThat(dto.getAuditStatus()).isZero(); verify(producer).send(dto); verify(publisher).publish(dto);
    }
    @Test void failClosedRecordsPendingWithoutBroadcast() {
        when(audit.audit("课程问题")).thenReturn(new DanmakuAuditService.Decision(0, "审核不可用"));
        assertThat(service(false).moderate(message(), () -> true).broadcast()).isFalse();
        verify(producer).send(any()); verifyNoInteractions(publisher);
    }
    @Test void recordingFailureCannotBroadcastOrClaimSuccess() {
        when(audit.audit("课程问题")).thenReturn(new DanmakuAuditService.Decision(1, null));
        doThrow(new IllegalStateException("storage unavailable")).when(producer).send(any());
        var result = service(true).moderate(message(), () -> true);
        assertThat(result.broadcast()).isFalse(); assertThat(result.code()).isEqualTo(503);
        verifyNoInteractions(publisher);
    }
    @Test void rejectionEvidenceSurvivesSessionRevocationDuringAudit() {
        var active = new AtomicBoolean(true);
        when(audit.audit("课程问题")).thenAnswer(call -> {
            active.set(false); return new DanmakuAuditService.Decision(2, "拒绝原因");
        });
        var dto = message();
        assertThat(service(true).moderate(dto, active::get).broadcast()).isFalse();
        verify(producer).send(dto);
        assertThat(dto.getAuditRemark()).isEqualTo("拒绝原因");
        verifyNoInteractions(publisher);
    }

    @Test void revokedSessionDuringAuditCannotBeDelivered() {
        var active = new AtomicBoolean(true);
        when(audit.audit("课程问题")).thenAnswer(call -> {
            active.set(false); return new DanmakuAuditService.Decision(1, null);
        });
        assertThat(service(true).moderate(message(), active::get).broadcast()).isFalse();
        verifyNoInteractions(producer, publisher);
    }
    @Test void fullQueueRejectsWithoutAuditingOnCaller() throws Exception {
        var executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1));
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var done = new CountDownLatch(2);
        when(audit.audit("课程问题")).thenAnswer(call -> {
            entered.countDown(); assertThat(release.await(3, TimeUnit.SECONDS)).isTrue();
            return new DanmakuAuditService.Decision(1, null);
        });
        var service = new DanmakuModerationService(audit, producer, publisher, executor, true, 30000);
        try {
            assertThat(service.submit(message(), () -> true, result -> done.countDown())).isTrue();
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(service.submit(message(), () -> true, result -> done.countDown())).isTrue();
            assertThat(service.submit(message(), () -> true, result -> fail("Rejected task ran"))).isFalse();
            verify(audit).audit("课程问题"); release.countDown();
            assertThat(done.await(3, TimeUnit.SECONDS)).isTrue();
        } finally { release.countDown(); executor.shutdownNow(); }
    }
    @Test void expiredQueuedMessageDoesNotAuditOrBroadcast() {
        var tasks = new java.util.ArrayList<Runnable>();
        var service = new DanmakuModerationService(audit, producer, publisher, tasks::add, true, 0);
        service.submit(message(), () -> true, result -> assertThat(result.code()).isEqualTo(503));
        tasks.get(0).run(); verifyNoInteractions(audit, producer, publisher);
    }
}
