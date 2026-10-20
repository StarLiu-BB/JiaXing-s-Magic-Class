package com.zhixue.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixue.common.mybatis.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** AI 会话：侧边栏「最近」里的一项 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_conversation")
public class AiConversation extends BaseEntity {

    private Long userId;
    private String title;
    /** 限定课程提问时的课程；会话创建时确定，之后不可更改 */
    private Long courseId;
    private LocalDateTime lastMessageTime;
}
