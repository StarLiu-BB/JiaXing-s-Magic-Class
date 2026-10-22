package com.zhixue.interaction.netty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zhixue.api.ai.RemoteAiService;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.security.config.SecurityProperties;
import com.zhixue.common.security.model.LoginUser;
import com.zhixue.common.security.session.LoginSessionRegistry;
import com.zhixue.interaction.domain.entity.Danmaku;
import com.zhixue.interaction.mapper.DanmakuMapper;
import com.zhixue.interaction.mq.DanmakuProducer;
import com.zhixue.interaction.redis.RedisMessageListener;
import com.zhixue.interaction.redis.RedisMessagePublisher;
import com.zhixue.interaction.sensitive.SensitiveWordFilter;
import com.zhixue.interaction.service.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DanmakuBroadcastGateTest {
    private final RemoteAiService ai = mock(RemoteAiService.class);
    private final DanmakuMapper mapper = mock(DanmakuMapper.class);
    private final RedisMessagePublisher publisher = mock(RedisMessagePublisher.class);
    private final LoginSessionRegistry sessions = mock(LoginSessionRegistry.class);
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    @SuppressWarnings("unchecked")
    private EmbeddedChannel channel(boolean failOpen) {
        var properties = new SecurityProperties(); properties.setInternalToken("test-only-internal");
        var audit = new DanmakuAuditService(ai, properties);
        var persistence = new DanmakuPersistenceService(mapper, audit);
        var producer = new DanmakuProducer((ObjectProvider<RabbitTemplate>) mock(ObjectProvider.class), persistence, json);
        ReflectionTestUtils.setField(producer, "mqMode", "stub");
        var moderation = new DanmakuModerationService(audit, producer, publisher, Runnable::run, failOpen, 30000);
        var filter = mock(SensitiveWordFilter.class);
        when(filter.filter(anyString())).thenAnswer(call -> call.getArgument(0));
        when(sessions.resolve("test-token")).thenReturn(LoginUser.builder().userId(1003L).build());
        var channel = new EmbeddedChannel(new DanmakuHandler(mock(RedisMessageListener.class), moderation, filter, json, sessions));
        channel.attr(WsHandshakeAuthHandler.USER_ID).set(1003L);
        channel.attr(WsHandshakeAuthHandler.ROOM_ID).set(2007L);
        channel.attr(WsHandshakeAuthHandler.TOKEN).set("test-token");
        return channel;
    }

    @Test void forgedApprovalCannotBypassAuditAndRejectedEvidenceIsRetained() throws Exception {
        when(ai.auditText(anyMap(), eq("true"), eq("test-only-internal"))).thenReturn(R.ok("不符合规则"));
        when(mapper.insert(any(Danmaku.class))).thenReturn(1);
        var channel = channel(true);
        try {
            channel.writeInbound(new TextWebSocketFrame("{\"userId\":1001,\"roomId\":9999,\"content\":\"课程问题\",\"auditStatus\":1,\"auditRemark\":\"OK\"}"));
            var captured = org.mockito.ArgumentCaptor.forClass(Danmaku.class);
            verify(mapper).insert(captured.capture());
            assertThat(captured.getValue().getAuditStatus()).isEqualTo(2);
            assertThat(captured.getValue().getUserId()).isEqualTo(1003L);
            assertThat(captured.getValue().getRoomId()).isEqualTo(2007L);
            assertThat(captured.getValue().getAuditRemark()).isEqualTo("不符合规则");
            verifyNoInteractions(publisher);
            var reply = (TextWebSocketFrame) channel.readOutbound();
            assertThat(json.readTree(reply.text()).path("code").asInt()).isEqualTo(400);
            reply.release();
        } finally { channel.finishAndReleaseAll(); }
    }

    @Test void approvedPipelineAuditsOnceAndWritesBeforeBroadcast() {
        when(ai.auditText(anyMap(), anyString(), anyString())).thenReturn(R.ok("OK"));
        when(mapper.insert(any(Danmaku.class))).thenReturn(1);
        var channel = channel(true);
        try {
            channel.writeInbound(new TextWebSocketFrame("{\"content\":\"课程问题\"}"));
            var order = inOrder(ai, mapper, publisher);
            order.verify(ai).auditText(anyMap(), anyString(), anyString());
            order.verify(mapper).insert(any(Danmaku.class)); order.verify(publisher).publish(any());
            verify(ai).auditText(anyMap(), anyString(), anyString());
        } finally { channel.finishAndReleaseAll(); }
    }

    @Test void failedEvidenceWriteCannotBroadcast() {
        when(ai.auditText(anyMap(), anyString(), anyString())).thenReturn(R.ok("OK"));
        var channel = channel(true);
        try {
            channel.writeInbound(new TextWebSocketFrame("{\"content\":\"课程问题\"}"));
            verify(mapper).insert(any(Danmaku.class)); verifyNoInteractions(publisher);
            var reply = (TextWebSocketFrame) channel.readOutbound(); assertThat(reply.text()).contains("503"); reply.release();
        } finally { channel.finishAndReleaseAll(); }
    }
}
