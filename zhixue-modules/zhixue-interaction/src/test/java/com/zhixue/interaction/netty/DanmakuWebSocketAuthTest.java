package com.zhixue.interaction.netty;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zhixue.common.core.constant.CacheConstants;
import com.zhixue.common.core.utils.JwtUtils;
import com.zhixue.common.redis.service.RedisService;
import com.zhixue.common.security.config.SecurityProperties;
import com.zhixue.common.security.model.LoginUser;
import com.zhixue.interaction.domain.dto.DanmakuMessageDTO;
import com.zhixue.interaction.mq.DanmakuProducer;
import com.zhixue.interaction.redis.RedisMessageListener;
import com.zhixue.interaction.redis.RedisMessagePublisher;
import com.zhixue.interaction.sensitive.SensitiveWordFilter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 弹幕 WebSocket 的握手鉴权与身份绑定。
 *
 * <p>修复前（实测）：</p>
 * <ul>
 *   <li>小程序连接 {@code /ws?roomId=2001}，而服务端只精确匹配 {@code /ws}，握手直接失败——学生端弹幕从未连通过</li>
 *   <li>19999 端口不经网关、不校验令牌，userId 取自消息体：任何人都能以任意用户身份发弹幕</li>
 *   <li>房间只在收到首条弹幕时登记：只看不发的观众收不到任何弹幕</li>
 *   <li>小程序每 30 秒发一次 {"type":"ping"}，服务端把它当弹幕解析并回 400</li>
 * </ul>
 */
class DanmakuWebSocketAuthTest {

    private static final String SECRET = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef-test".getBytes());

    private final com.zhixue.common.security.session.LoginSessionRegistry sessions = mock(com.zhixue.common.security.session.LoginSessionRegistry.class);
    private final RedisMessageListener roomRegistry = mock(RedisMessageListener.class);
    private final DanmakuProducer producer = mock(DanmakuProducer.class);
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private WsHandshakeAuthHandler authHandler;
    private DanmakuHandler danmakuHandler;
    private String token;

    @BeforeEach
    void setUp() {
        SecurityProperties props = new SecurityProperties();
        props.setJwtSecret(SECRET);
        authHandler = new WsHandshakeAuthHandler(sessions, props, roomRegistry);
        SensitiveWordFilter filter = mock(SensitiveWordFilter.class);
        when(filter.filter(anyString())).thenAnswer(inv -> inv.getArgument(0));
        danmakuHandler = new DanmakuHandler(roomRegistry, mock(RedisMessagePublisher.class), producer, filter, objectMapper);

        token = JwtUtils.generateToken("1003", Map.of(), SECRET, 600);
        LoginUser user = new LoginUser();
        user.setUserId(1003L);
        user.setUsername("student");
        when(sessions.resolve(token)).thenReturn(user);
    }

    private static FullHttpRequest handshake(String uri, String authorization) {
        FullHttpRequest req = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
        if (authorization != null) {
            req.headers().set("Authorization", authorization);
        }
        return req;
    }

    private EmbeddedChannel channel() {
        return new EmbeddedChannel(authHandler);
    }

    @Test
    void validTokenInHeaderPassesWithQueryStrippedAndJoinsRoom() {
        EmbeddedChannel ch = channel();
        ch.writeInbound(handshake("/ws?roomId=2001", "Bearer " + token));

        FullHttpRequest forwarded = ch.readInbound();
        assertThat(forwarded).as("握手请求应继续交给 WebSocket 协议处理器").isNotNull();
        assertThat(forwarded.uri()).as("必须去掉查询串，否则精确匹配 /ws 失败").isEqualTo("/ws");
        assertThat(ch.attr(WsHandshakeAuthHandler.USER_ID).get()).isEqualTo(1003L);
        assertThat(ch.attr(WsHandshakeAuthHandler.ROOM_ID).get()).isEqualTo(2001L);
        verify(roomRegistry).addChannel(ch, 2001L);
    }

    @Test
    void tokenMayAlsoComeFromQueryParameter() {
        EmbeddedChannel ch = channel();
        ch.writeInbound(handshake("/ws?roomId=2001&token=" + token, null));
        assertThat((Object) ch.readInbound()).isNotNull();
        assertThat(ch.attr(WsHandshakeAuthHandler.USER_ID).get()).isEqualTo(1003L);
    }

    @Test
    void missingTokenIsRejectedWith401() {
        EmbeddedChannel ch = channel();
        ch.writeInbound(handshake("/ws?roomId=2001", null));

        assertThat((Object) ch.readInbound()).isNull();
        FullHttpResponse resp = ch.readOutbound();
        assertThat(resp.status()).isEqualTo(HttpResponseStatus.UNAUTHORIZED);
        verify(roomRegistry, never()).addChannel(any(), anyLong());
    }

    @Test
    void loggedOutTokenIsRejectedWith401() {
        when(sessions.resolve(token)).thenReturn(null);
        EmbeddedChannel ch = channel();
        ch.writeInbound(handshake("/ws?roomId=2001", "Bearer " + token));

        assertThat((Object) ch.readInbound()).isNull();
        assertThat(((FullHttpResponse) ch.readOutbound()).status()).isEqualTo(HttpResponseStatus.UNAUTHORIZED);
    }

    @Test
    void missingRoomIsRejectedWith400() {
        EmbeddedChannel ch = channel();
        ch.writeInbound(handshake("/ws", "Bearer " + token));
        assertThat((Object) ch.readInbound()).isNull();
        assertThat(((FullHttpResponse) ch.readOutbound()).status()).isEqualTo(HttpResponseStatus.BAD_REQUEST);
    }

    @Test
    void senderIdentityAndRoomComeFromHandshakeNotFromMessageBody() {
        EmbeddedChannel ch = new EmbeddedChannel(danmakuHandler);
        ch.attr(WsHandshakeAuthHandler.USER_ID).set(1003L);
        ch.attr(WsHandshakeAuthHandler.ROOM_ID).set(2001L);

        // 伪造成管理员 1001、发到另一个房间
        ch.writeInbound(new TextWebSocketFrame("{\"roomId\":9999,\"userId\":1001,\"content\":\"讲得好\",\"timePoint\":12}"));

        ArgumentCaptor<DanmakuMessageDTO> captor = ArgumentCaptor.forClass(DanmakuMessageDTO.class);
        verify(producer).send(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1003L);
        assertThat(captor.getValue().getRoomId()).isEqualTo(2001L);
        assertThat(captor.getValue().getContent()).isEqualTo("讲得好");
    }

    @Test
    void heartbeatIsAnsweredWithPongAndNotTreatedAsDanmaku() {
        EmbeddedChannel ch = new EmbeddedChannel(danmakuHandler);
        ch.attr(WsHandshakeAuthHandler.USER_ID).set(1003L);
        ch.attr(WsHandshakeAuthHandler.ROOM_ID).set(2001L);

        ch.writeInbound(new TextWebSocketFrame("{\"type\":\"ping\"}"));

        TextWebSocketFrame reply = ch.readOutbound();
        assertThat(reply.text()).isEqualTo("{\"type\":\"pong\"}");
        verify(producer, never()).send(any());
    }

    @Test
    void unauthenticatedChannelCannotSend() {
        EmbeddedChannel ch = new EmbeddedChannel(danmakuHandler);
        ch.writeInbound(new TextWebSocketFrame("{\"roomId\":2001,\"userId\":1003,\"content\":\"x\"}"));
        verify(producer, never()).send(any());
        assertThat(((TextWebSocketFrame) ch.readOutbound()).text()).contains("401");
    }

    @Test
    void registryIsNotTouchedTwiceForSameRoom() {
        // 握手时已登记房间，发送弹幕时不应再按消息体里的 roomId 重新登记
        EmbeddedChannel ch = new EmbeddedChannel(danmakuHandler);
        ch.attr(WsHandshakeAuthHandler.USER_ID).set(1003L);
        ch.attr(WsHandshakeAuthHandler.ROOM_ID).set(2001L);
        ch.writeInbound(new TextWebSocketFrame("{\"roomId\":9999,\"content\":\"hi\"}"));
        verify(roomRegistry, never()).addChannel(any(), eq(9999L));
        assertThat(List.of(ch.attr(WsHandshakeAuthHandler.ROOM_ID).get())).containsExactly(2001L);
    }
}
