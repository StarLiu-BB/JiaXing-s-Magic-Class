package com.zhixue.ai.controller;

import com.zhixue.ai.domain.entity.KnowledgeDocument;
import com.zhixue.ai.service.KnowledgeBaseService;
import com.zhixue.ai.service.LlmService;
import com.zhixue.ai.service.PromptTemplateService;
import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.exception.GlobalExceptionHandler;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.security.config.InternalAccessConstants;
import com.zhixue.common.security.config.SecurityAccessAspect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * AI 接口访问控制（经真实鉴权切面）。
 *
 * <p>修复前三个接口都没有任何权限注解，且 AiApplication 未扫描 com.zhixue.common，
 * 鉴权切面与内部请求校验根本不存在：学生可以往知识库写入会被拼进所有人提示词的内容，
 * 任何登录用户都能把审核接口当作免费模型调用。</p>
 */
class AiAccessControlTest {

    private final LlmService llmService = mock(LlmService.class);
    private final KnowledgeBaseService kbService = mock(KnowledgeBaseService.class);
    private final PromptTemplateService templateService = mock(PromptTemplateService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        proxy(new AiChatController(llmService, kbService, templateService)),
                        proxy(new KnowledgeBaseController(kbService)),
                        proxy(new AuditController(llmService)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(llmService.chat(anyString(), anyString())).thenReturn(Mono.just("答案"));
        when(llmService.audit(anyString())).thenReturn(Mono.just("OK"));
        when(kbService.createFromText(any(), any(), any(), any())).thenReturn(new KnowledgeDocument());
    }

    private static Object proxy(Object controller) {
        AspectJProxyFactory factory = new AspectJProxyFactory(controller);
        factory.setProxyTargetClass(true);
        factory.addAspect(new SecurityAccessAspect());
        return factory.getProxy();
    }

    private static MockHttpServletRequestBuilder asUser(MockHttpServletRequestBuilder b, long userId, String... perms) {
        return b.requestAttr(InternalAccessConstants.TRUSTED_REQUEST_ATTR, Boolean.TRUE)
                .header(InternalAccessConstants.USER_ID_HEADER, String.valueOf(userId))
                .header(InternalAccessConstants.USER_PERMISSIONS_HEADER, String.join(",", perms));
    }

    /** 答疑/审核返回 Mono，需要再做一次异步派发才能拿到响应体 */
    private ResultActions perform(MockHttpServletRequestBuilder builder) throws Exception {
        ResultActions actions = mockMvc.perform(builder);
        MvcResult result = actions.andReturn();
        return result.getRequest().isAsyncStarted() ? mockMvc.perform(asyncDispatch(result)) : actions;
    }

    private static MockMultipartFile txt() {
        return new MockMultipartFile("file", "spring.txt", "text/plain", "网关负责统一鉴权".getBytes());
    }

    @Test
    void anonymousCannotAsk() throws Exception {
        perform(get("/chat/course").param("question", "网关是什么")).andExpect(jsonPath("$.code").value(401));
        verify(llmService, never()).chat(anyString(), anyString());
    }

    @Test
    void loggedInStudentGetsAnswerScopedToCourse() throws Exception {
        when(kbService.retrieveContext(eq("网关是什么"), anyInt(), eq(2001L))).thenReturn(List.of("《网关》统一鉴权"));

        perform(asUser(get("/chat/course"), 1003L).param("question", "网关是什么").param("courseId", "2001"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("答案"));

        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(llmService).chat(anyString(), user.capture());
        // 修复前种子模板缺少占位符，模型既收不到资料也收不到问题
        assertThat(user.getValue()).contains("《网关》统一鉴权").contains("网关是什么");
    }

    @Test
    void modelOutageIsReportedAsFailureNotAsAnswer() throws Exception {
        when(kbService.retrieveContext(anyString(), anyInt(), isNull())).thenReturn(List.of());
        when(llmService.chat(anyString(), anyString()))
                .thenReturn(Mono.error(new ServiceException(HttpStatus.SERVICE_UNAVAILABLE, "AI 服务暂不可用，请稍后再试")));

        perform(asUser(get("/chat/course"), 1003L).param("question", "网关是什么"))
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.msg").value("AI 服务暂不可用，请稍后再试"));
    }

    @Test
    void overlongQuestionIsRejectedBeforeReachingTheModel() throws Exception {
        perform(asUser(get("/chat/course"), 1003L).param("question", "问".repeat(501)))
                .andExpect(jsonPath("$.code").value(400));
        verify(llmService, never()).chat(anyString(), anyString());
    }

    @Test
    void studentCannotUploadKnowledge() throws Exception {
        perform(asUser(multipart("/kb/upload").file(txt()), 1003L)).andExpect(jsonPath("$.code").value(403));
        verify(kbService, never()).createFromText(any(), any(), any(), any());
    }

    @Test
    void editorUploadsKnowledgeForACourse() throws Exception {
        perform(asUser(multipart("/kb/upload").file(txt()).param("courseId", "2001"), 1001L, "ai:kb:edit"))
                .andExpect(jsonPath("$.code").value(200));
        verify(kbService).createFromText(eq("spring.txt"), eq("course:2001"), isNull(), eq("网关负责统一鉴权"));
    }

    @Test
    void uploadRejectsNonTextFiles() throws Exception {
        MockMultipartFile exe = new MockMultipartFile("file", "a.exe", "application/octet-stream", new byte[]{1});
        perform(asUser(multipart("/kb/upload").file(exe), 1001L, "ai:kb:edit")).andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void auditIsNotReachableThroughTheGateway() throws Exception {
        // 经网关转发的请求带可信令牌但没有 X-Inner-Call（网关会剥掉它）
        perform(asUser(post("/audit/text"), 1003L).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"你好\"}"))
                .andExpect(jsonPath("$.code").value(403));
        verify(llmService, never()).audit(anyString());
    }

    @Test
    void internalServiceCanAudit() throws Exception {
        perform(post("/audit/text")
                .requestAttr(InternalAccessConstants.TRUSTED_REQUEST_ATTR, Boolean.TRUE)
                .header(InternalAccessConstants.INNER_CALL_HEADER, "true")
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"你好\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("OK"));
    }
}
