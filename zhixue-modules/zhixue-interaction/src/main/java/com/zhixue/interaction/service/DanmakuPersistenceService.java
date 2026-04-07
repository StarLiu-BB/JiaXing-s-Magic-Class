package com.zhixue.interaction.service;

import com.zhixue.api.ai.RemoteAiService;
import com.zhixue.common.core.domain.R;
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
    private final RemoteAiService remoteAiService;

    public void persist(DanmakuMessageDTO dto) {
        Danmaku entity = new Danmaku();
        entity.setRoomId(dto.getRoomId());
        entity.setUserId(dto.getUserId());
        entity.setContent(dto.getContent());
        entity.setTimePoint(dto.getTimePoint());

        int status = 1;
        String remark = null;
        try {
            R<String> auditResult = remoteAiService.auditText(Map.of("content", dto.getContent()));
            if (auditResult != null && auditResult.isSuccess()) {
                String result = auditResult.getData();
                if (result != null && !"OK".equalsIgnoreCase(result.trim())) {
                    status = 2;
                    remark = result;
                }
            } else if (auditResult != null) {
                log.warn("AI 审核返回失败，按通过处理，msg={}", auditResult.getMsg());
            } else {
                log.warn("AI 审核返回空结果，按通过处理");
            }
        } catch (Exception e) {
            log.error("AI 审核异常，按通过处理", e);
        }

        entity.setAuditStatus(status);
        entity.setAuditRemark(remark);
        danmakuMapper.insert(entity);
    }
}
