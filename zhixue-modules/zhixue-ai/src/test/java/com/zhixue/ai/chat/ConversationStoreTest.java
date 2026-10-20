package com.zhixue.ai.chat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixue.ai.domain.entity.AiConversation;
import com.zhixue.ai.domain.entity.AiMessage;
import com.zhixue.ai.mapper.AiConversationMapper;
import com.zhixue.ai.mapper.AiMessageMapper;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.exception.ServiceException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationStoreTest {

    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AiConversation.class);
        TableInfoHelper.initTableInfo(assistant, AiMessage.class);
    }

    private final AiConversationMapper conversationMapper = mock(AiConversationMapper.class);
    private final AiMessageMapper messageMapper = mock(AiMessageMapper.class);
    private final ConversationStore store = new ConversationStore(conversationMapper, messageMapper);

    @Test
    void titleIsFirstTwentyCharactersWithWhitespaceCollapsed() {
        assertThat(ConversationStore.titleOf("  HashMap\n为什么  线程不安全？请详细讲讲它在多线程下的问题  "))
                .isEqualTo("HashMap 为什么 线程不安全？请详");
        assertThat(ConversationStore.titleOf("   ")).isEqualTo("新对话");
    }

    @Test
    void otherUsersConversationLooksExactlyLikeAMissingOne() {
        when(conversationMapper.selectById(5L)).thenReturn(conversation(5L, 1099L));

        assertThatThrownBy(() -> store.requireOwned(5L, 1003L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("会话不存在或已删除")
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(404));
        assertThatThrownBy(() -> store.requireOwned(6L, 1003L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("会话不存在或已删除");
    }

    @Test
    void historyKeepsLastThreeCompletedTurnsOnly() {
        // 数据库按 id 倒序返回最近的消息；第 2 轮的回答失败了，不能带进上下文
        List<AiMessage> desc = new ArrayList<>(List.of(
                message(10L, "assistant", "A5", MessageStatus.COMPLETED), message(9L, "user", "Q5", 1),
                message(8L, "assistant", "A4", MessageStatus.COMPLETED), message(7L, "user", "Q4", 1),
                message(6L, "assistant", "A3", MessageStatus.COMPLETED), message(5L, "user", "Q3", 1),
                message(4L, "assistant", "", MessageStatus.FAILED), message(3L, "user", "Q2", 1),
                message(2L, "assistant", "A1", MessageStatus.COMPLETED), message(1L, "user", "Q1", 1)));
        when(messageMapper.selectList(any())).thenReturn(desc);

        List<ConversationStore.Turn> turns = store.completedHistory(100L, 11L, 3);

        assertThat(turns).containsExactly(
                new ConversationStore.Turn("Q3", "A3"),
                new ConversationStore.Turn("Q4", "A4"),
                new ConversationStore.Turn("Q5", "A5"));
    }

    @Test
    void renameValidatesLengthAndOwnership() {
        when(conversationMapper.selectById(5L)).thenReturn(conversation(5L, 1003L));

        assertThatThrownBy(() -> store.rename(5L, 1003L, "  "))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(400));
        assertThatThrownBy(() -> store.rename(5L, 1003L, "长".repeat(65)))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(400));

        store.rename(5L, 1003L, "  并发笔记  ");
        ArgumentCaptor<AiConversation> update = ArgumentCaptor.forClass(AiConversation.class);
        verify(conversationMapper).updateById(update.capture());
        assertThat(update.getValue().getTitle()).isEqualTo("并发笔记");
    }

    @Test
    void deletingSomeoneElsesConversationIs404AndDeletesNothing() {
        when(conversationMapper.selectById(5L)).thenReturn(conversation(5L, 1099L));

        assertThatThrownBy(() -> store.delete(5L, 1003L)).isInstanceOf(ServiceException.class);
        verify(conversationMapper, never()).deleteById(anyLong());
    }

    @Test
    @SuppressWarnings("unchecked")
    void pageSizeIsCappedAndIdsAreStrings() {
        AiConversation c = conversation(2112171801568825346L, 1003L);
        c.setTitle("HashMap 线程安全");
        when(conversationMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<AiConversation> page = invocation.getArgument(0);
            page.setRecords(List.of(c));
            page.setTotal(1);
            return page;
        });

        PageResult<ConversationView> result = store.listRecent(1003L, 1, 500);

        ArgumentCaptor<Page<AiConversation>> page = ArgumentCaptor.forClass(Page.class);
        verify(conversationMapper).selectPage(page.capture(), any());
        assertThat(page.getValue().getSize()).isEqualTo(50);
        assertThat(result.getRecords()).extracting(ConversationView::id).containsExactly("2112171801568825346");
    }

    @Test
    void recommendationsRoundTripThroughJson() {
        AiConversation c = conversation(100L, 1003L);
        store.saveAssistantMessage(200L, c, "回答", MessageStatus.COMPLETED, List.of(new Source("Java 并发编程实战 · 线程池")),
                List.of(new StoredRecommendation("2007", 0.66, List.of("4025"))), 1234L);
        ArgumentCaptor<AiMessage> saved = ArgumentCaptor.forClass(AiMessage.class);
        verify(messageMapper).insert(saved.capture());

        assertThat(saved.getValue().getId()).isEqualTo(200L);
        assertThat(store.recommendationsOf(saved.getValue()))
                .containsExactly(new StoredRecommendation("2007", 0.66, List.of("4025")));
        assertThat(store.sourcesOf(saved.getValue())).containsExactly(new Source("Java 并发编程实战 · 线程池"));
    }

    private static AiConversation conversation(long id, long userId) {
        AiConversation c = new AiConversation();
        c.setId(id);
        c.setUserId(userId);
        c.setTitle("会话");
        return c;
    }

    private static AiMessage message(long id, String role, String content, int status) {
        AiMessage m = new AiMessage();
        m.setId(id);
        m.setConversationId(100L);
        m.setUserId(1003L);
        m.setRole(role);
        m.setContent(content);
        m.setStatus(status);
        return m;
    }
}
