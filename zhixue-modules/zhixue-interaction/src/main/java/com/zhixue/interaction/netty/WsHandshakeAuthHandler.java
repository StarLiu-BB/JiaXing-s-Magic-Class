package com.zhixue.interaction.netty;

import com.zhixue.common.core.constant.CacheConstants;
import com.zhixue.common.core.utils.JwtUtils;
import com.zhixue.common.redis.service.RedisService;
import com.zhixue.common.security.config.SecurityProperties;
import com.zhixue.common.security.model.LoginUser;
import com.zhixue.interaction.redis.RedisMessageListener;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 弹幕 WebSocket 握手鉴权：19999 端口不经过网关，必须自己校验登录态。
 *
 * <ul>
 *   <li>令牌取自 {@code Authorization: Bearer}（小程序 wx.connectSocket 的 header），或查询参数 {@code token}</li>
 *   <li>与网关一致：JWT 验签 + Redis 中存在登录记录（退出登录后令牌立即失效）</li>
 *   <li>房间取自查询参数 {@code roomId}；握手时即登记房间，只看不发的观众也能收到弹幕</li>
 *   <li>去掉查询串再交给 WebSocketServerProtocolHandler：它精确匹配 /ws，带查询串的握手会被拒绝</li>
 * </ul>
 * 发送者身份与房间存入连接属性，后续弹幕一律以此为准，不信任消息体。
 */
@Slf4j
@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class WsHandshakeAuthHandler extends ChannelInboundHandlerAdapter {

    public static final AttributeKey<Long> USER_ID = AttributeKey.valueOf("zhixue.ws.userId");
    public static final AttributeKey<Long> ROOM_ID = AttributeKey.valueOf("zhixue.ws.roomId");

    private static final String BEARER = "Bearer ";

    private final RedisService redisService;
    private final SecurityProperties securityProperties;
    private final RedisMessageListener roomRegistry;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof FullHttpRequest request)) {
            ctx.fireChannelRead(msg);
            return;
        }
        QueryStringDecoder query = new QueryStringDecoder(request.uri());
        Long roomId = parseLong(first(query, "roomId"));
        if (roomId == null) {
            reject(ctx, request, HttpResponseStatus.BAD_REQUEST, "缺少房间号 roomId");
            return;
        }
        LoginUser user = resolveUser(extractToken(request, query));
        if (user == null || user.getUserId() == null) {
            reject(ctx, request, HttpResponseStatus.UNAUTHORIZED, "未登录或登录已过期");
            return;
        }
        ctx.channel().attr(USER_ID).set(user.getUserId());
        ctx.channel().attr(ROOM_ID).set(roomId);
        roomRegistry.addChannel(ctx.channel(), roomId);
        request.setUri(query.path());
        ctx.fireChannelRead(request);
    }

    private String extractToken(FullHttpRequest request, QueryStringDecoder query) {
        String header = request.headers().get(HttpHeaderNames.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER)) {
            return header.substring(BEARER.length()).trim();
        }
        return first(query, "token");
    }

    private LoginUser resolveUser(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            JwtUtils.parseClaims(token, securityProperties.getJwtSecret());
            String key = CacheConstants.LOGIN_TOKEN_KEY + token;
            if (!redisService.hasKey(key)) {
                return null;
            }
            Object cached = redisService.get(key);
            return cached instanceof LoginUser loginUser ? loginUser : null;
        } catch (Exception e) {
            log.debug("弹幕握手令牌无效: {}", e.getMessage());
            return null;
        }
    }

    private static void reject(ChannelHandlerContext ctx, FullHttpRequest request, HttpResponseStatus status, String reason) {
        ReferenceCountUtil.release(request);
        FullHttpResponse response = new DefaultFullHttpResponse(request.protocolVersion(), status,
                Unpooled.copiedBuffer(reason, StandardCharsets.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private static String first(QueryStringDecoder query, String name) {
        List<String> values = query.parameters().get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private static Long parseLong(String value) {
        try {
            return value == null ? null : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
