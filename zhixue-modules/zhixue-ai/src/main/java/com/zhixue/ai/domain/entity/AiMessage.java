package com.zhixue.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixue.common.mybatis.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 会话中的一条消息；sources 与 recommendations 以 JSON 字符串存取 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_message")
public class AiMessage extends BaseEntity {

    private Long conversationId;
    /** 冗余的归属，按消息查询时直接校验 */
    private Long userId;
    /** user / assistant */
    private String role;
    private String content;
    /** 见 MessageStatus */
    private Integer status;
    /** 引用资料标题，JSON 数组 */
    private String sources;
    /** [{courseId, score, lessonIds}]，只有 ID 与分数 */
    private String recommendations;
    private Integer costMs;
}
