package com.zhixue.interaction.netty;

import com.zhixue.common.security.session.LoginSessionRegistry;
import com.zhixue.common.security.model.LoginUser;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WsSessionGuardTest {
    @Test void revokedEstablishedConnectionCannotSend() {
        LoginSessionRegistry sessions = mock(LoginSessionRegistry.class);
        EmbeddedChannel channel = new EmbeddedChannel(new WsSessionGuard(sessions));
        channel.attr(WsHandshakeAuthHandler.TOKEN).set("revoked");
        channel.attr(WsHandshakeAuthHandler.USER_ID).set(1003L);
        channel.writeInbound(new TextWebSocketFrame("{\"type\":\"ping\"}"));
        assertThat((Object) channel.readInbound()).isNull();
        assertThat(((CloseWebSocketFrame) channel.readOutbound()).statusCode()).isEqualTo(1008);
        channel.finishAndReleaseAll();
    }

    @Test void revokedViewerCannotReceiveFurtherBroadcasts() {
        LoginSessionRegistry sessions = mock(LoginSessionRegistry.class);
        EmbeddedChannel channel = new EmbeddedChannel(new WsSessionGuard(sessions));
        channel.attr(WsHandshakeAuthHandler.TOKEN).set("revoked");
        channel.attr(WsHandshakeAuthHandler.USER_ID).set(1003L);
        assertThatThrownBy(() -> channel.writeOutbound(new TextWebSocketFrame("broadcast")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("登录已失效");
        assertThat(((CloseWebSocketFrame) channel.readOutbound()).statusCode()).isEqualTo(1008);
        channel.finishAndReleaseAll();
    }

    @Test void validSessionPassesBothDirections() {
        LoginSessionRegistry sessions = mock(LoginSessionRegistry.class);
        when(sessions.resolve("valid")).thenReturn(LoginUser.builder().userId(1003L).build());
        EmbeddedChannel channel = new EmbeddedChannel(new WsSessionGuard(sessions));
        channel.attr(WsHandshakeAuthHandler.TOKEN).set("valid");
        channel.attr(WsHandshakeAuthHandler.USER_ID).set(1003L);
        channel.writeInbound(new TextWebSocketFrame("incoming"));
        assertThat(((TextWebSocketFrame) channel.readInbound()).text()).isEqualTo("incoming");
        channel.writeOutbound(new TextWebSocketFrame("outgoing"));
        assertThat(((TextWebSocketFrame) channel.readOutbound()).text()).isEqualTo("outgoing");
        channel.finishAndReleaseAll();
    }
}
