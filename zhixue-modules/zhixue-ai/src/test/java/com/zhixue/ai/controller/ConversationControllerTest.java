package com.zhixue.ai.controller;

import com.zhixue.ai.chat.ConversationStore;
import com.zhixue.ai.chat.ConversationView;
import com.zhixue.ai.chat.CourseRecommender;
import com.zhixue.ai.chat.RecommendationCard;
import com.zhixue.ai.chat.StoredRecommendation;
import com.zhixue.ai.chat.Viewer;
import com.zhixue.ai.domain.entity.AiConversation;
import com.zhixue.ai.domain.entity.AiMessage;
import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.exception.GlobalExceptionHandler;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.security.config.InternalAccessConstants;
import com.zhixue.common.security.config.SecurityAccessAspect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class ConversationControllerTest {

    private final ConversationStore store = mock(ConversationStore.class);
    private final CourseRecommender recommender = mock(CourseRecommender.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new ConversationController(store, recommender));
        factory.setProxyTargetClass(true);
        factory.addAspect(new SecurityAccessAspect());
        mockMvc = MockMvcBuilders.standaloneSetup((Object) factory.getProxy())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static MockHttpServletRequestBuilder asStudent(MockHttpServletRequestBuilder builder) {
        return builder.requestAttr(InternalAccessConstants.TRUSTED_REQUEST_ATTR, Boolean.TRUE)
                .header(InternalAccessConstants.USER_ID_HEADER, "1003")
                .header(InternalAccessConstants.USER_ROLES_HEADER, "STUDENT");
    }

    @Test
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/conversations")).andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void listsOwnConversations() throws Exception {
        when(store.listRecent(1003L, 1, 20)).thenReturn(PageResult.of(List.of(
                new ConversationView("2112171801568825346", "HashMap 线程安全", null, LocalDateTime.now())), 1, 20));

        mockMvc.perform(asStudent(get("/conversations")))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].id").value("2112171801568825346"));
    }

    @Test
    void detailReturnsOwnCourseScopeAndHidesOthers() throws Exception {
        AiConversation owned = new AiConversation();
        owned.setId(5L);
        owned.setUserId(1003L);
        owned.setTitle("并发笔记");
        owned.setCourseId(2007L);
        owned.setLastMessageTime(LocalDateTime.now());
        when(store.requireOwned(5L, 1003L)).thenReturn(owned);
        when(store.requireOwned(6L, 1003L)).thenThrow(new ServiceException(HttpStatus.NOT_FOUND, "会话不存在或已删除"));

        mockMvc.perform(asStudent(get("/conversations/5")))
                .andExpect(jsonPath("$.data.id").value("5"))
                .andExpect(jsonPath("$.data.courseId").value("2007"));
        mockMvc.perform(asStudent(get("/conversations/6")))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void othersConversationIs404() throws Exception {
        when(store.requireOwned(5L, 1003L)).thenThrow(new ServiceException(HttpStatus.NOT_FOUND, "会话不存在或已删除"));

        mockMvc.perform(asStudent(get("/conversations/5/messages")))
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg").value("会话不存在或已删除"));
    }

    @Test
    void messagesCarryCardsResolvedInOneBatch() throws Exception {
        AiMessage question = message(1L, "user", "HashMap 为什么线程不安全");
        AiMessage answer = message(2L, "assistant", "因为……");
        List<StoredRecommendation> stored = List.of(new StoredRecommendation("2007", 0.66, List.of("4024")));
        when(store.messagesOf(5L)).thenReturn(List.of(question, answer));
        when(store.recommendationsOf(question)).thenReturn(List.of());
        when(store.recommendationsOf(answer)).thenReturn(stored);
        when(store.sourcesOf(question)).thenReturn(List.of());
        when(store.sourcesOf(answer)).thenReturn(List.of());
        RecommendationCard card = new RecommendationCard("2007", "Java 并发编程实战", "c.png",
                new BigDecimal("99.00"), true, false, List.of());
        when(recommender.resolve(eq(List.of(List.of(), stored)), eq(new Viewer(1003L, false))))
                .thenReturn(List.of(List.of(), List.of(card)));

        mockMvc.perform(asStudent(get("/conversations/5/messages")))
                .andExpect(jsonPath("$.data[0].id").value("1"))
                .andExpect(jsonPath("$.data[1].recommendations[0].courseId").value("2007"))
                .andExpect(jsonPath("$.data[1].recommendations[0].purchased").value(true));
        verify(recommender, times(1)).resolve(anyList(), eq(new Viewer(1003L, false)));
    }

    @Test
    void renameAndDeleteDelegateWithCurrentUser() throws Exception {
        mockMvc.perform(asStudent(put("/conversations/5")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"并发笔记\"}"))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(asStudent(delete("/conversations/5"))).andExpect(jsonPath("$.code").value(200));

        verify(store).rename(5L, 1003L, "并发笔记");
        verify(store).delete(5L, 1003L);
    }

    private static AiMessage message(long id, String role, String content) {
        AiMessage m = new AiMessage();
        m.setId(id);
        m.setConversationId(5L);
        m.setUserId(1003L);
        m.setRole(role);
        m.setContent(content);
        m.setStatus(1);
        return m;
    }
}
