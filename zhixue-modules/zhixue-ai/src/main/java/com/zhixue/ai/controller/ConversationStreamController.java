package com.zhixue.ai.controller;

import com.zhixue.ai.chat.AskRequest;
import com.zhixue.ai.chat.ConversationChatService;
import com.zhixue.ai.chat.Viewer;
import com.zhixue.common.security.annotation.RequireLogin;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 流式提问。返回 Flux&lt;ServerSentEvent&gt; 时 Spring MVC 自动按 text/event-stream 输出。
 *
 * <p>不声明 produces：流前错误由全局异常处理器按 JSON 返回，声明了 text/event-stream 会导致 JSON 无法输出。
 * 前端先看 Content-Type 再决定读流还是读 JSON（以 body.code 为准）。</p>
 */
@RestController
@RequestMapping("/conversations")
@RequiredArgsConstructor
public class ConversationStreamController {

    private final ConversationChatService chatService;

    @RequireLogin
    @PostMapping("/messages")
    public Flux<ServerSentEvent<String>> ask(@RequestBody AskRequest request, HttpServletResponse response) {
        // 身份必须在请求线程上取：进入流式处理后读不到网关注入的请求头
        Flux<ServerSentEvent<String>> events = chatService.ask(Viewer.current(), request);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        // 经 Nginx 部署时关闭代理缓冲，否则整段回答会被攒到最后才发出
        response.setHeader("X-Accel-Buffering", "no");
        return events;
    }
}
