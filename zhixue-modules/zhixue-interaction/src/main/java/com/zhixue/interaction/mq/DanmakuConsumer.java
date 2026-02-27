package com.zhixue.interaction.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.api.ai.RemoteAiService;
import com.zhixue.common.core.domain.R;
import com.zhixue.interaction.domain.dto.DanmakuMessageDTO;
import com.zhixue.interaction.domain.entity.Danmaku;
import com.zhixue.interaction.mapper.DanmakuMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 弹幕 MQ 消费者，异步写库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DanmakuConsumer {

    private final DanmakuMapper danmakuMapper;
    private final RemoteAiService remoteAiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @RabbitListener(queuesToDeclare = @Queue("${interaction.danmaku-queue:queue_danmaku}"))
    public void onMessage(String message) {
        try {
            DanmakuMessageDTO dto = objectMapper.readValue(message, DanmakuMessageDTO.class);
            Danmaku entity = new Danmaku();
            entity.setRoomId(dto.getRoomId());
            entity.setUserId(dto.getUserId());
            entity.setContent(dto.getContent());
            entity.setTimePoint(dto.getTimePoint());
            
            // 调用 AI 审核
            int status = 1; // 默认审核通过
            String remark = null;
            try {
                R<String> auditResult = remoteAiService.auditText(Map.of("content", dto.getContent()));
                if (R.SUCCESS == auditResult.getCode()) {
                    if (!"OK".equalsIgnoreCase(auditResult.getData())) {
                        status = 2; // 审核拒绝
                        remark = auditResult.getData();
                    }
                } else {
                    log.warn("AI 审核调用失败: {}", auditResult.getMsg());
                    // 审核服务异常时，可以默认通过或人工审核，这里选择默认通过但记录日志
                }
            } catch (Exception e) {
                log.error("AI 审核异常", e);
            }

            entity.setAuditStatus(status);
            entity.setAuditRemark(remark);
            danmakuMapper.insert(entity);
        } catch (Exception e) {
            log.error("消费弹幕消息失败, body={}", message, e);
        }
    }
}
