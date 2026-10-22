package com.zhixue.interaction.service;

import com.zhixue.api.ai.RemoteAiService;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.security.config.SecurityProperties;
import com.zhixue.interaction.domain.dto.DanmakuMessageDTO;
import com.zhixue.interaction.domain.entity.Danmaku;
import com.zhixue.interaction.mapper.DanmakuMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 弹幕落库服务，支持 MQ 消费与 stub 直写两种入口复用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DanmakuPersistenceService {

    private final DanmakuMapper danmakuMapper;
    private final DanmakuAuditService auditService;

    public void persist(DanmakuMessageDTO dto) {
        String messageId = dto.getMessageId();
        if (messageId != null && (!messageId.matches("[A-Za-z0-9-]{1,64}"))) {
            throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("弹幕消息ID不合法");
        }
        if (messageId != null) {
            Danmaku existing = danmakuMapper.findMessage(messageId);
            if (existing != null) { requireSameMessage(existing, dto); return; }
        }
        Danmaku entity = new Danmaku();
        entity.setMessageId(messageId);
        entity.setRoomId(dto.getRoomId());
        entity.setUserId(dto.getUserId());
        entity.setContent(dto.getContent());
        entity.setTimePoint(dto.getTimePoint());

        Integer status = dto.getAuditStatus();
        String remark = dto.getAuditRemark();
        if (status == null) {
            DanmakuAuditService.Decision decision = auditService.audit(dto.getContent());
            status = decision.status();
            remark = decision.remark();
        }
        if (status < 0 || status > 2) throw new com.zhixue.common.core.exception.ServiceException("审核状态不合法");
        entity.setAuditStatus(status);
        entity.setAuditRemark(remark == null ? null : remark.substring(0, Math.min(remark.length(), 255)));
        try {
            if (danmakuMapper.insert(entity) != 1) throw new com.zhixue.common.core.exception.ServiceException("审核记录保存失败");
        } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            Danmaku existing = messageId == null ? null : danmakuMapper.findMessage(messageId);
            if (existing == null) throw duplicate;
            requireSameMessage(existing, dto);
        }
    }

    private void requireSameMessage(Danmaku existing, DanmakuMessageDTO dto) {
        if (!java.util.Objects.equals(existing.getRoomId(), dto.getRoomId())
                || !java.util.Objects.equals(existing.getUserId(), dto.getUserId())
                || !java.util.Objects.equals(existing.getContent(), dto.getContent())
                || !java.util.Objects.equals(java.util.Objects.requireNonNullElse(existing.getTimePoint(), 0),
                        java.util.Objects.requireNonNullElse(dto.getTimePoint(), 0))) {
            throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("消息ID冲突，请人工核对");
        }
    }
}
