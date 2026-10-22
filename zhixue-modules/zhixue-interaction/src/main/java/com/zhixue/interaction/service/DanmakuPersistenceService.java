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
        Danmaku entity = new Danmaku();
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
        if (danmakuMapper.insert(entity) != 1) throw new com.zhixue.common.core.exception.ServiceException("审核记录保存失败");
    }
}
