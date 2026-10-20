package com.zhixue.ai.controller;

import com.zhixue.ai.chat.ConversationChatService;
import com.zhixue.ai.chat.Viewer;
import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.exception.GlobalExceptionHandler;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.security.config.InternalAccessConstants;
import com.zhixue.common.security.config.SecurityAccessAspect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConversationStreamControllerTest {

    private static final String BODY = "{\"question\":\"HashMap 为什么线程不安全\"}";

    private final ConversationChatService chatService = mock(ConversationChatService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new ConversationStreamController(chatService));
        factory.setProxyTargetClass(true);
        factory.addAspect(new SecurityAccessAspect());
        mockMvc = MockMvcBuilders.standaloneSetup((Object) factory.getProxy())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static MockHttpServletRequestBuilder asStudent(MockHttpServletRequestBuilder builder) {
        return builder.requestAttr(InternalAccessConstants.TRUSTED_REQUEST_ATTR, Boolean.TRUE)
                .header(InternalAccessConstants.USER_ID_HEADER, "1003")
                .header(InternalAccessConstants.USER_ROLES_HEADER, "STUDENT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY);
    }

    @Test
    void streamsServerSentEventsWithProxyBufferingDisabled() throws Exception {
        when(chatService.ask(eq(new Viewer(1003L, false)), any())).thenReturn(Flux.just(
                ServerSentEvent.builder("{\"conversationId\":\"100\"}").event("meta").build(),
                ServerSentEvent.builder("{\"text\":\"答\"}").event("delta").build(),
                ServerSentEvent.builder("{\"recommendations\":[]}").event("done").build()));

        MvcResult result = mockMvc.perform(asStudent(post("/conversations/messages")))
                .andExpect(request().asyncStarted())
                .andReturn();
        result.getAsyncResult(5000);

        MockHttpServletResponse response = result.getResponse();
        assertThat(response.getContentType()).startsWith("text/event-stream");
        assertThat(response.getHeader("X-Accel-Buffering")).isEqualTo("no");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-cache");
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("event:meta").contains("event:delta").contains("event:done");
        assertThat(body.indexOf("event:meta")).isLessThan(body.indexOf("event:done"));
    }

    @Test
    void errorBeforeStreamingIsPlainJsonWithCodeInBody() throws Exception {
        when(chatService.ask(any(), any())).thenThrow(new ServiceException(HttpStatus.CONFLICT, "上一个回答还在生成"));

        // 前端同时接受两种类型（见学生网页版计划任务 4）：流前错误仍须按 JSON 返回，不能 406
        mockMvc.perform(asStudent(post("/conversations/messages")).accept("text/event-stream, application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.msg").value("上一个回答还在生成"));
    }

    /** 反过来：Accept 里同时有 application/json 时，成功路径仍必须是 text/event-stream */
    @Test
    void streamsEvenWhenClientAlsoAcceptsJson() throws Exception {
        when(chatService.ask(eq(new Viewer(1003L, false)), any())).thenReturn(Flux.just(
                ServerSentEvent.builder("{\"text\":\"答\"}").event("delta").build()));

        MvcResult result = mockMvc.perform(asStudent(post("/conversations/messages"))
                        .accept("text/event-stream, application/json"))
                .andExpect(request().asyncStarted())
                .andReturn();
        result.getAsyncResult(5000);

        assertThat(result.getResponse().getContentType()).startsWith("text/event-stream");
    }

    @Test
    void requiresLogin() throws Exception {
        mockMvc.perform(post("/conversations/messages").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(jsonPath("$.code").value(401));
    }
}
