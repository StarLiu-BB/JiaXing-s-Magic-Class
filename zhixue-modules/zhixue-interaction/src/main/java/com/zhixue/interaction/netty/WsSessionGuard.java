package com.zhixue.interaction.netty;

import com.zhixue.common.security.model.LoginUser;
import com.zhixue.common.security.session.LoginSessionRegistry;
import io.netty.channel.*;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.ReferenceCountUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class WsSessionGuard extends ChannelDuplexHandler {
    private final LoginSessionRegistry sessions;

    private boolean valid(ChannelHandlerContext ctx) {
        try {
            LoginUser user = sessions.resolve(ctx.channel().attr(WsHandshakeAuthHandler.TOKEN).get());
            return user != null && java.util.Objects.equals(user.getUserId(), ctx.channel().attr(WsHandshakeAuthHandler.USER_ID).get());
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof TextWebSocketFrame && !valid(ctx)) {
            ReferenceCountUtil.release(msg);
            ctx.writeAndFlush(new CloseWebSocketFrame(1008, "登录已失效，请重新登录")).addListener(ChannelFutureListener.CLOSE);
            return;
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof TextWebSocketFrame && !valid(ctx)) {
            ReferenceCountUtil.release(msg);
            promise.tryFailure(new IllegalStateException("登录已失效"));
            ctx.writeAndFlush(new CloseWebSocketFrame(1008, "登录已失效，请重新登录")).addListener(ChannelFutureListener.CLOSE);
            return;
        }
        ctx.write(msg, promise);
    }
}
