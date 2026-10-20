package com.zhixue.ai.controller;

import com.zhixue.ai.chat.ConversationStore;
import com.zhixue.ai.chat.ConversationView;
import com.zhixue.ai.chat.CourseRecommender;
import com.zhixue.ai.chat.MessageView;
import com.zhixue.ai.chat.RecommendationCard;
import com.zhixue.ai.chat.StoredRecommendation;
import com.zhixue.ai.chat.Viewer;
import com.zhixue.ai.domain.entity.AiMessage;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.security.annotation.RequireLogin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话管理（侧边栏「最近」、打开历史、重命名、删除）。流式提问在 ConversationStreamController。
 * 网关路径带 /ai 前缀（StripPrefix=1）。
 */
@RestController
@RequestMapping("/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationStore store;
    private final CourseRecommender recommender;

    public record RenameRequest(String title) {
    }

    @RequireLogin
    @GetMapping
    public R<PageResult<ConversationView>> list(@RequestParam(defaultValue = "1") int pageNum,
                                                @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(store.listRecent(Viewer.current().userId(), pageNum, pageSize));
    }

    /** 单个会话：学生网页版打开不在「最近」前 50 条里的会话时，用它取课程范围 */
    @RequireLogin
    @GetMapping("/{id}")
    public R<ConversationView> detail(@PathVariable Long id) {
        return R.ok(ConversationView.of(store.requireOwned(id, Viewer.current().userId())));
    }

    @RequireLogin
    @GetMapping("/{id}/messages")
    public R<List<MessageView>> messages(@PathVariable Long id) {
        Viewer viewer = Viewer.current();
        store.requireOwned(id, viewer.userId());
        List<AiMessage> messages = store.messagesOf(id);
        List<List<StoredRecommendation>> stored = messages.stream().map(store::recommendationsOf).toList();
        // 卡片按最新的价格、购买与上架状态解析，全部消息一次批量查询
        List<List<RecommendationCard>> cards = recommender.resolve(stored, viewer);
        List<MessageView> views = new ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            AiMessage message = messages.get(i);
            views.add(MessageView.of(message, store.sourcesOf(message), cards.get(i)));
        }
        return R.ok(views);
    }

    @RequireLogin
    @PutMapping("/{id}")
    public R<Void> rename(@PathVariable Long id, @RequestBody RenameRequest request) {
        store.rename(id, Viewer.current().userId(), request == null ? null : request.title());
        return R.ok();
    }

    @RequireLogin
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        store.delete(id, Viewer.current().userId());
        return R.ok();
    }
}
